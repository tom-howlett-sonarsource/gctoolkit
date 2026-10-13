package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.stream.Collectors.toList;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void countsAllDiscoveredFilesWithoutChangingOrdering() throws Exception {
        Path first = Files.writeString(directory.resolve("gc.log.0"), "[1.000s] first\n[3.000s] last\n");
        Path overlapping = Files.writeString(directory.resolve("gc.log.1"), "[2.000s] first\n[4.000s] last\n");
        Path active = Files.writeString(directory.resolve("gc.log"), "[5.000s] active\n");
        long expected = Files.size(first) + Files.size(overlapping) + Files.size(active);
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);
        assertEquals(expected, metadata.getTotalByteSize());
        List<String> order = metadata.logFiles().map(LogFileSegment::getSegmentName).collect(toList());
        assertEquals(expected, metadata.getTotalByteSize());
        assertEquals(order, metadata.logFiles().map(LogFileSegment::getSegmentName).collect(toList()));
    }

    @Test
    void discoversSetFromArchivedOrActiveMember() throws Exception {
        Path archived = Files.write(directory.resolve("gc.log.0"), new byte[17]);
        Path active = Files.write(directory.resolve("gc.log"), new byte[23]);
        Files.write(directory.resolve("other.log"), new byte[100]);
        assertEquals(40L, new RotatingLogFileMetadata(archived).getTotalByteSize());
        assertEquals(40L, new RotatingLogFileMetadata(active).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndArchives() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        for (boolean directoryEntry : new boolean[]{false, true}) {
            Path archive = directory.resolve("empty-" + directoryEntry + ".zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
                if (directoryEntry) {
                    output.putNextEntry(new ZipEntry("logs/"));
                    output.closeEntry();
                }
            }
            assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
        }
    }

    @Test
    void countsEveryUncompressedZipEntryWithoutRequiringRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        byte[] content = "repeated log text\n".repeat(1000).getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : new String[]{"logs/", "logs/first.txt", "logs/second.txt"}) {
                output.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/"))
                    output.write(content);
                output.closeEntry();
            }
        }
        assertEquals(2L * content.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void excludesSubdirectoriesFromByteSize() throws Exception {
        Files.createDirectory(directory.resolve("nested"));
        Files.write(directory.resolve("gc.log"), new byte[9]);
        assertEquals(9L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }
}

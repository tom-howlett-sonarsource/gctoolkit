package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsFromAnyMemberWithoutIncludingUnrelatedFilesOrDirectories() throws Exception {
        Path active = Files.writeString(directory.resolve("gc.log"), "active\n");
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "rotated\n");
        Files.writeString(directory.resolve("other.log"), "unrelated\n");
        Files.createDirectory(directory.resolve("gc.log.directory"));

        long expected = Files.size(active) + Files.size(rotated);
        assertEquals(expected, new RotatingLogFileMetadata(active).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndDirectoryOnlyZip() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void preservesDiscoveredSegmentOrder() throws Exception {
        Path active = Files.writeString(directory.resolve("gc.log"), "active\n");
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "rotated\n");
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);
        long expected = Files.size(active) + Files.size(rotated);

        assertEquals(expected, metadata.getTotalByteSize());
        List<String> names = metadata.logFiles()
                .map(LogFileSegment::getSegmentName).collect(Collectors.toList());
        assertEquals(expected, metadata.getTotalByteSize());
        assertEquals(names, metadata.logFiles()
                .map(LogFileSegment::getSegmentName).collect(Collectors.toList()));
    }

    @Test
    void sumsZipEntriesWithoutRequiringRotatingNamesOrTimestampOrdering() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : new String[] {"first.txt", "nested/second.txt"}) {
                output.putNextEntry(new ZipEntry(name));
                output.write(new byte[4096]);
                output.closeEntry();
            }
        }
        assertEquals(8192L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

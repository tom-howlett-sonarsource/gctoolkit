package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsTheSetFromEitherActiveOrRotatedMember() throws Exception {
        Path active = Files.writeString(directory.resolve("gc.log"), "active\n");
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "rotated\n");
        Files.writeString(directory.resolve("other.log"), "unrelated\n");
        long expected = Files.size(active) + Files.size(rotated);

        assertEquals(expected, new RotatingLogFileMetadata(active).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void countsOverlappingSegmentsWithoutChangingTheirOrdering() throws Exception {
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "[2.000s] rotated\n");
        Path active = Files.writeString(directory.resolve("gc.log"), "[1.000s] active\n");
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);
        var before = metadata.logFiles().map(LogFileSegment::getSegmentName)
                .collect(java.util.stream.Collectors.toList());

        assertEquals(Files.size(rotated) + Files.size(active), metadata.getTotalByteSize());
        assertEquals(before, metadata.logFiles().map(LogFileSegment::getSegmentName)
                .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void returnsZeroForEmptyDirectoryAndDirectoriesOnlyArchive() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Path archive = directory.resolve("directories.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void sumsAllZipEntriesWithoutRequiringRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        byte[] content = new byte[8192];
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String name : new String[]{"nested/first.txt", "second.txt"}) {
                output.putNextEntry(new ZipEntry(name));
                output.write(content);
                output.closeEntry();
            }
        }
        assertEquals(2L * content.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

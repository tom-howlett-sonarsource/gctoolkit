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
    void sumsEveryDiscoveredMemberRegardlessOfOrdering() throws Exception {
        Path current = Files.writeString(directory.resolve("gc.log"), "[1.0s] current\n");
        Path first = Files.writeString(directory.resolve("gc.log.0"), "[2.0s] first\n");
        Path second = Files.writeString(directory.resolve("gc.log.1"), "[3.0s] second\n");
        Files.writeString(directory.resolve("unrelated.log"), "unrelated\n");
        long expected = Files.size(current) + Files.size(first) + Files.size(second);

        for (Path member : new Path[]{current, first, second}) {
            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(member);
            assertEquals(expected, metadata.getTotalByteSize());
            long orderedCount = metadata.logFiles().count();
            assertEquals(expected, metadata.getTotalByteSize());
            assertEquals(orderedCount, metadata.logFiles().count());
        }
    }

    @Test
    void sumsLegacyCurrentAndRotatedMembers() throws Exception {
        Path current = Files.writeString(directory.resolve("gc.log.1.current"), "current\n");
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "rotated\n");
        assertEquals(Files.size(current) + Files.size(rotated),
                new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroForDirectoryWithoutRegularFiles() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Files.createDirectory(directory.resolve("nested"));
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void sumsAllUncompressedZipEntriesWithoutRequiringRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        byte[] content = new byte[8192];
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            for (String name : new String[]{"nested/first.txt", "second.txt"}) {
                output.putNextEntry(new ZipEntry(name));
                output.write(content);
                output.closeEntry();
            }
        }
        assertEquals(2L * content.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyAndDirectoryOnlyArchives() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

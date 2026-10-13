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
    void sumsSetFromEitherActiveOrRotatedMember() throws Exception {
        Path active = Files.writeString(directory.resolve("gc.log"), "active\n");
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "rotated\n");
        Files.writeString(directory.resolve("other.log"), "unrelated\n");
        long expected = Files.size(active) + Files.size(rotated);

        assertEquals(expected, new RotatingLogFileMetadata(active).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void includesLegacyCurrentSegment() throws Exception {
        Path active = Files.writeString(directory.resolve("gc.log.1.current"), "active\n");
        Path rotated = Files.writeString(directory.resolve("gc.log.0"), "rotated\n");

        assertEquals(Files.size(active) + Files.size(rotated),
                new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndIgnoresSubdirectories() throws Exception {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);
        assertEquals(0L, metadata.getTotalByteSize());
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Files.writeString(nested.resolve("gc.log"), "not a direct segment\n");
        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void sumsArbitraryZipEntriesWithoutRequiringRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        byte[] content = new byte[8192];
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("nested/first.txt"));
            output.write(content);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.txt"));
            output.write(content);
            output.closeEntry();
        }
        assertEquals(2L * content.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyAndDirectoryOnlyArchives() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.finish();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

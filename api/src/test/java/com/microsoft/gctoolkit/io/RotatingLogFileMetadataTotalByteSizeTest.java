// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    private static final byte[] ROTATED = "rotated segment\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURRENT = "current segment\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void emptyDirectoryHasNoBytes() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void nestedDirectoriesAreNotCounted() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.createDirectory(logs.resolve("nested"));
        Files.write(logs.resolve("gc.log"), CURRENT);
        assertEquals(CURRENT.length, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void singleMemberOfRotatingSetSumsItsSiblings() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log.1"), ROTATED);
        Path current = logs.resolve("gc.log");
        Files.write(current, CURRENT);
        // an unrelated log in the same directory must not be counted
        Files.write(logs.resolve("other.log"), CURRENT);

        long expected = (2L * ROTATED.length) + CURRENT.length;
        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(logs.resolve("gc.log.1")).getTotalByteSize());
    }

    @Test
    void preUnifiedCurrentSuffixIsIncluded() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Path current = logs.resolve("gc.log.1.current");
        Files.write(current, CURRENT);

        long expected = ROTATED.length + CURRENT.length;
        assertEquals(expected, new RotatingLogFileMetadata(logs).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoryEntriesHasNoBytes() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void zipReportsUncompressedSizeRatherThanCompressedSize() throws Exception {
        byte[] compressible = new byte[64 * 1024];
        Path archive = directory.resolve("compressible.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(compressible);
            output.closeEntry();
        }
        assertEquals(compressible.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
        assertEquals(compressible.length > Files.size(archive), true);
    }

    @Test
    void sizeCoversSegmentsThatOrderingDiscards() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log"), CURRENT);

        // Segments without parseable time stamps are dropped by the existing ordering pass;
        // the byte total is taken from discovery so every segment on disk is still counted.
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);
        assertEquals(1, metadata.getNumberOfFiles());
        assertEquals(ROTATED.length + CURRENT.length, metadata.getTotalByteSize());
    }
}

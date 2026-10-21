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

    @Test
    void sumsAllSegmentsWhenConstructedFromMember() throws Exception {
        byte[] first = "first\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("other.txt"), new byte[100]);
        long expected = first.length + current.length;
        assertEquals(expected, new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(directory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipWithOnlyDirectories() throws Exception {
        Path archive = directory.resolve("dirs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

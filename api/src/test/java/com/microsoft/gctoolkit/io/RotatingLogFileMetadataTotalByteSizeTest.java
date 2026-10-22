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
    void sumsWholeSetWhenConstructedFromIndividualMember() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second entry\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        // Construct from an individual member of the rotating-file set.
        long total = new RotatingLogFileMetadata(directory.resolve("gc.log.1")).getTotalByteSize();

        assertEquals(first.length + second.length + current.length, total);
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipWithOnlyDirectoryEntries() throws Exception {
        Path archive = directory.resolve("dirs-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

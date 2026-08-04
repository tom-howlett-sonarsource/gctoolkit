// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSetWhenConstructedFromRotatedMember() throws Exception {
        byte[] rotated = "rotated log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path rotatedMember = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(rotatedMember).getTotalByteSize());
    }

    @Test
    void returnsZeroForDirectoryWithoutFiles() throws Exception {
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipWithoutFileEntries() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(archive))) {
            // An empty ZIP has no eligible entries.
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

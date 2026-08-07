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

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSetWhenConstructedFromRotatedMember() throws Exception {
        byte[] rotated = "rotated\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active\n".getBytes(StandardCharsets.UTF_8);
        Path rotatedPath = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("other.log"), new byte[20]);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(rotatedPath).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());

        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(archive))) {
            // Empty archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

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

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromRotatingSetMember() throws Exception {
        byte[] rotated = "rotated log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path rotatedLog = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(rotatedLog).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipContainingOnlyDirectories() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

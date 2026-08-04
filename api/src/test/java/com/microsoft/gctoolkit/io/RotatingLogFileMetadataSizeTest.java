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
    void sumsSetWhenConstructedFromRotatedMember() throws Exception {
        byte[] rotated = "rotated log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path rotatedMember = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("unrelated.log"), new byte[19]);

        assertEquals(rotated.length + current.length,
                new RotatingLogFileMetadata(rotatedMember).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoEligibleEntries() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.createDirectory(logs.resolve("nested"));

        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenZipHasNoEligibleEntries() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

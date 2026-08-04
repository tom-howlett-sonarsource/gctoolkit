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
    void sumsRotatingSetWhenConstructedFromMember() throws Exception {
        byte[] oldest = "oldest log\n".getBytes(StandardCharsets.UTF_8);
        byte[] rotated = "rotated log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path oldestLog = Files.write(directory.resolve("gc.log.0"), oldest);
        Files.write(directory.resolve("gc.log.1"), rotated);
        Files.write(directory.resolve("gc.log"), current);

        assertEquals(oldest.length + rotated.length + current.length,
                new RotatingLogFileMetadata(oldestLog).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenNoEligibleEntriesExist() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

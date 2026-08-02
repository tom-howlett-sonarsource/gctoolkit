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

class RotatingLogFileMetadataSizeAdditionalTest {

    @TempDir
    Path directory;

    @Test
    void sumsSetWhenConstructedFromRotatedMember() throws Exception {
        byte[] rotated = "rotated".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log".getBytes(StandardCharsets.UTF_8);
        Path rotatedMember = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("other.log"), new byte[31]);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(rotatedMember).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenNoEligibleEntriesExist() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        Files.createDirectory(emptyDirectory.resolve("nested"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

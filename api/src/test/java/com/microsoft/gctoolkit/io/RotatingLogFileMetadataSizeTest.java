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
    void sumsRotatingSetWhenConstructedFromNumberedMember() throws Exception {
        byte[] first = "[0.001s] first\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "[1.001s] second\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "[2.001s] current\n".getBytes(StandardCharsets.UTF_8);
        Path firstSegment = Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("other.log"), new byte[100]);

        assertEquals(first.length + second.length + current.length,
                new RotatingLogFileMetadata(firstSegment).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZipWithOnlyDirectoryEntries() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

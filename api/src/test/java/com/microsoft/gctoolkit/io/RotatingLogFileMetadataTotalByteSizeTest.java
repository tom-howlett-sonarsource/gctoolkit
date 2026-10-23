// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromMember() throws Exception {
        Files.write(directory.resolve("gc.log.0"), new byte[7]);
        Files.write(directory.resolve("gc.log"), new byte[5]);
        Files.write(directory.resolve("other.txt"), new byte[100]);
        assertEquals(12L, new RotatingLogFileMetadata(directory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void emptyDirectoryIsZero() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoriesIsZero() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

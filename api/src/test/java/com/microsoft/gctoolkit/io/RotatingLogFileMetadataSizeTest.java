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

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsRotatingSetFromIndividualMember() throws Exception {
        Files.write(directory.resolve("gc.log.0"), new byte[7]);
        Files.write(directory.resolve("gc.log.1"), new byte[5]);
        Files.write(directory.resolve("gc.log"), new byte[3]);
        Files.write(directory.resolve("other.txt"), new byte[100]);
        assertEquals(15L, new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(15L, new RotatingLogFileMetadata(directory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void emptyDirectoryHasZeroSize() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoriesHasZeroSize() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

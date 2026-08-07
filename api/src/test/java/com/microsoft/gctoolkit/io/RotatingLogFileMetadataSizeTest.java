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
    void discoversSetFromIndividualMember() throws Exception {
        Path first = Files.write(directory.resolve("gc.log.0"), new byte[] {1, 2, 3});
        Files.write(directory.resolve("gc.log"), new byte[] {4, 5});

        assertEquals(5L, new RotatingLogFileMetadata(first).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyInputs() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());

        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

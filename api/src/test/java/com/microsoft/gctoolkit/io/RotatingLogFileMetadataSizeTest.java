// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversSetFromAnIndividualMember() throws Exception {
        Path first = Files.write(directory.resolve("gc.log.0"), new byte[]{1, 2, 3});
        Files.write(directory.resolve("gc.log"), new byte[]{4, 5});

        assertEquals(5L, new RotatingLogFileMetadata(first).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyInputs() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Empty archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyZip).getTotalByteSize());
    }
}

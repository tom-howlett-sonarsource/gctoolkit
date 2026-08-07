// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversSetFromIndividualMember() throws Exception {
        Path current = Files.write(directory.resolve("gc.log"), new byte[7]);
        Path rotated = Files.write(directory.resolve("gc.log.0"), new byte[5]);

        assertEquals(12L, new RotatingLogFileMetadata(rotated).getTotalByteSize());
        assertEquals(12L, new RotatingLogFileMetadata(current).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());

        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(archive))) {
            // Create an empty ZIP archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

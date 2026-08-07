// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataAdditionalSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSetWhenConstructedFromIndividualMember() throws Exception {
        Path rotated = Files.write(directory.resolve("gc.log.0"), new byte[7]);
        Files.write(directory.resolve("gc.log"), new byte[11]);

        assertEquals(18L, new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Deliberately empty.
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyZip).getTotalByteSize());
    }

    @Test
    void returnsZeroForUnsupportedCompressedInput() throws Exception {
        Path gzip = directory.resolve("gc.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(new byte[9]);
        }

        assertEquals(0L, new RotatingLogFileMetadata(gzip).getTotalByteSize());
    }
}

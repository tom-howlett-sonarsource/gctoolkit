// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataAdditionalSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversAllSegmentsFromAnIndividualMember() throws Exception {
        byte[] oldest = "oldest\n".getBytes(StandardCharsets.UTF_8);
        byte[] rotated = "rotated\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), oldest);
        Path member = Files.write(directory.resolve("gc.log.1"), rotated);
        Files.write(directory.resolve("gc.log"), active);

        assertEquals(oldest.length + rotated.length + active.length,
                new RotatingLogFileMetadata(member).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Create an empty, valid ZIP archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyZip).getTotalByteSize());
    }
}

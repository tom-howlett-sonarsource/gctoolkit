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

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversAllSegmentsWhenConstructedFromSetMember() throws Exception {
        byte[] previous = "[0.001s] previous log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "[1.000s] active log\n".getBytes(StandardCharsets.UTF_8);
        Path previousSegment = Files.write(directory.resolve("gc.log.0"), previous);
        Files.write(directory.resolve("gc.log"), active);

        assertEquals(previous.length + active.length,
                new RotatingLogFileMetadata(previousSegment).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Create an empty ZIP archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyZip).getTotalByteSize());
    }
}

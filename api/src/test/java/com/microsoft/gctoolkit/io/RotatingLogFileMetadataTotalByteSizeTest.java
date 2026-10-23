// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void sumsSegmentsWhenConstructedFromIndividualMember() throws Exception {
        byte[] first = "first\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        // Construct from a single member of the rotating-file set.
        long total = new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize();
        assertEquals(first.length + second.length + current.length, total);
    }
}

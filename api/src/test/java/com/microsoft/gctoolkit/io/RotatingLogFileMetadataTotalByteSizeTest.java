// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSiblingSegmentsWhenConstructedFromAnIndividualMember() throws IOException {
        Path first = Files.write(directory.resolve("G1-80-16gbps2.log.0"), "first log\n".getBytes());
        Path current = Files.write(directory.resolve("G1-80-16gbps2.log"), "current log\n".getBytes());

        long totalByteSize = new RotatingLogFileMetadata(current).getTotalByteSize();

        assertEquals(Files.size(first) + Files.size(current), totalByteSize);
    }

    @Test
    void returnsZeroForEmptyDirectory() throws IOException {
        Path empty = Files.createDirectory(directory.resolve("empty"));

        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }
}

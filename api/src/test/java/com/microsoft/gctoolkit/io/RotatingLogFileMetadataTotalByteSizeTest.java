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
    void sumsSegmentsWhenConstructedFromAnIndividualMemberOfTheSet() throws IOException {
        Path currentFile = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        Path firstFile = currentFile.resolveSibling("G1-80-16gbps2.log.0");
        long expected = Files.size(currentFile) + Files.size(firstFile);

        assertEquals(expected, new RotatingLogFileMetadata(currentFile).getTotalByteSize());
    }

    @Test
    void returnsZeroForAnEmptyDirectory() throws IOException {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));

        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());
    }
}

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
    void constructedFromIndividualMemberSumsWholeRotatingSet() throws IOException {
        Path current = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        Path segment = new TestLogFile("G1-80-16gbps2.log.0").getFile().toPath();
        long expected = Files.size(current) + Files.size(segment);

        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(segment).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoSegments() throws IOException {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void isUnaffectedByPriorLogFilesInvocation() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), "first log\n".getBytes());
        Files.write(logs.resolve("gc.log"), "current log\n".getBytes());

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);
        metadata.logFiles().forEach(segment -> {}); // force discovery/ordering as a caller might
        assertEquals(Files.size(logs.resolve("gc.log.0")) + Files.size(logs.resolve("gc.log")),
                metadata.getTotalByteSize());
    }
}

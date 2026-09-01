// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SingleGCLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsNonBlankLinesAndEndOfDataSentinel() throws IOException {
        Path logFile = temporaryDirectory.resolve("gc.log");
        Files.writeString(logFile, " first \n\nsecond\n");

        SingleGCLogFile singleGCLogFile = new SingleGCLogFile(logFile);

        try (Stream<String> lines = singleGCLogFile.stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }

    @Test
    void cachesMetadata() throws IOException {
        Path logFile = temporaryDirectory.resolve("gc.log");
        Files.writeString(logFile, "first\n");
        SingleGCLogFile singleGCLogFile = new SingleGCLogFile(logFile);

        LogFileMetadata metadata = singleGCLogFile.getMetaData();

        assertSame(metadata, singleGCLogFile.getMetaData());
    }
}

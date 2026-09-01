// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.GCLogFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SafepointLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesPathDiaryAndEndOfData() {
        Path logFile = temporaryDirectory.resolve("safepoint.log");
        SafepointLogFile safepointLogFile = new SafepointLogFile(logFile);

        assertEquals(logFile, safepointLogFile.getPath());
        assertNotNull(safepointLogFile.diary());
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, safepointLogFile.endOfData());
    }

    @Test
    void streamsPlainTextSafepointLog() throws IOException {
        Path logFile = temporaryDirectory.resolve("safepoint.log");
        Files.writeString(logFile, "first\nsecond\n");
        SafepointLogFile safepointLogFile = new SafepointLogFile(logFile);

        try (Stream<String> lines = safepointLogFile.stream()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }
}

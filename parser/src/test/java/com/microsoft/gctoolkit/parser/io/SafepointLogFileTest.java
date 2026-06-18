// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import com.microsoft.gctoolkit.io.GCLogFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SafepointLogFileTest {

    @TempDir
    Path tempDir;

    @Test
    void streamsSafepointLogFile() throws IOException {
        Path path = tempDir.resolve("safepoint.log");
        Files.write(path, List.of("safepoint-line"), StandardCharsets.UTF_8);
        SafepointLogFile logFile = new SafepointLogFile(path);

        assertEquals(path, logFile.getPath());
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, logFile.endOfData());
        assertNotNull(logFile.diary());
        assertEquals(List.of("safepoint-line"), lines(logFile.stream()));
    }

    private List<String> lines(Stream<String> stream) {
        try (stream) {
            return stream.collect(Collectors.toList());
        }
    }
}

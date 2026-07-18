// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleGCLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesLineFilteringAndEndOfDataSentinel() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        Files.write(path, List.of(" first ", "", "  ", "second"), StandardCharsets.UTF_8);
        SingleGCLogFile logFile = new SingleGCLogFile(path);

        try (var lines = logFile.stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
        assertEquals(Files.size(path), logFile.getMetaData().getSize());
    }
}

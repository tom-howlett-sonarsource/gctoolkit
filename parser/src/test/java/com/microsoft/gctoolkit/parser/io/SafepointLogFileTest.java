// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SafepointLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void opensPlainLogSource() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log");
        Files.write(path, List.of("first", "second"), StandardCharsets.UTF_8);

        try (var lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }
}

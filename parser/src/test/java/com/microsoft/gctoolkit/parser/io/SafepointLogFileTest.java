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
    void streamsSourceLines() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log");
        Files.writeString(path, "first\nsecond\n", StandardCharsets.UTF_8);

        try (var stream = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("first", "second"), stream.collect(toList()));
        }
    }
}

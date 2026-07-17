// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafepointLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsSafepointLogThroughSharedSource() throws IOException {
        Path path = Files.writeString(temporaryDirectory.resolve("safepoint.log"), "first\nsecond\n");

        try (Stream<String> lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }
}

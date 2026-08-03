// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversAndOpensPlainSource() throws IOException {
        Path source = Files.writeString(directory.resolve("gc.log"), "line\n", StandardCharsets.UTF_8);

        assertEquals(LogFileSource.Format.PLAINTEXT, LogFileSource.discover(source));
        assertEquals(Files.size(source), LogFileSource.byteSize(source));
        try (var lines = LogFileSource.open(source)) {
            assertEquals("line", lines.findFirst().orElseThrow());
        }
    }
}

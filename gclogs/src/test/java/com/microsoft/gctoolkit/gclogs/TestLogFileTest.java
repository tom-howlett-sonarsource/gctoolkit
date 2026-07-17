// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void wrapsFileAndReportsItsSize() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log");
        Files.writeString(log, "123456789");

        TestLogFile testLogFile = new TestLogFile(log.toFile());

        assertEquals(log.toFile(), testLogFile.getFile());
        assertEquals(log.toString(), testLogFile.getPath());
        assertEquals(9L, testLogFile.getSize());
    }

    @Test
    void discoversLogFromKnownSourceDirectories() throws IOException {
        TestLogFile testLogFile = new TestLogFile("preunified/ps/empty.log");

        assertEquals("empty.log", testLogFile.getFile().getName());
        assertEquals(Files.size(testLogFile.getFile().toPath()), testLogFile.getSize());
    }

    @Test
    void rejectsMissingLog() {
        assertThrows(IllegalArgumentException.class, () -> new TestLogFile("missing-gc-log.log"));
    }
}

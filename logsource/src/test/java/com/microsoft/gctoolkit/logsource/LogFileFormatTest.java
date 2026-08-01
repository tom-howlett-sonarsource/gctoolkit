// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileFormatTest {

    @TempDir
    Path directory;

    @Test
    void discoversPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.discover(LogSourceFixture.plainText(directory, "gc.log")));
    }

    @Test
    void discoversGZip() throws IOException {
        assertEquals(LogFileFormat.GZIP, LogFileFormat.discover(LogSourceFixture.gzip(directory, "gc.log.gz")));
    }

    @Test
    void discoversZip() throws IOException {
        assertEquals(LogFileFormat.ZIP, LogFileFormat.discover(LogSourceFixture.zip(directory, "gc.zip", "gc.log")));
    }

    @Test
    void discoversDirectory() {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormat.discover(directory));
    }

    @Test
    void reportsAnUnreadableSourceAsPlainText() {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.discover(directory.resolve("missing.log")));
    }

    @Test
    void reportsAnEmptySourceAsPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.discover(LogSourceFixture.empty(directory, "empty.log")));
    }

    @Test
    void matchesMagicBytes() throws IOException {
        Path gzip = LogSourceFixture.gzip(directory, "gc.log.gz");
        assertTrue(LogFileFormat.magic(gzip, 0x1F, 0x8b));
        assertFalse(LogFileFormat.magic(gzip, 0x50, 0x4b));
    }

    @Test
    void magicIsFalseForAMissingSource() {
        assertFalse(LogFileFormat.magic(directory.resolve("missing.log"), 0x1F, 0x8b));
    }
}

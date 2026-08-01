// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeEmpty;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeGzip;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writePlainText;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeZip;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceFormatTest {

    @TempDir
    Path directory;

    @Test
    void discoversPlainText() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(writePlainText(directory, "gc.log", "a line")));
    }

    @Test
    void discoversGZip() throws IOException {
        assertEquals(LogSourceFormat.GZIP, LogSourceFormat.of(writeGzip(directory, "gc.log.gz", "a line")));
    }

    @Test
    void discoversZip() throws IOException {
        assertEquals(LogSourceFormat.ZIP, LogSourceFormat.of(writeZip(directory, "gc.zip", "gc.log", "a line")));
    }

    @Test
    void discoversDirectory() {
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceFormat.of(directory));
    }

    @Test
    void formatIsDiscoveredFromContentRatherThanName() throws IOException {
        assertEquals(LogSourceFormat.GZIP, LogSourceFormat.of(writeGzip(directory, "misleading.log", "a line")));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(writePlainText(directory, "misleading.gz", "a line")));
    }

    @Test
    void anEmptyFileIsPlainText() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(writeEmpty(directory, "empty.log")));
    }

    @Test
    void aSourceThatCannotBeReadIsPlainText() {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceFormat.of(directory.resolve("does-not-exist.log")));
    }

    @Test
    void aMissingSourceIsUnknown() {
        assertEquals(LogSourceFormat.UNKNOWN, LogSourceFormat.of(null));
    }
}

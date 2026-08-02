// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileFormatTest {

    @TempDir
    Path directory;

    @Test
    void detectsPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.of(TestSources.plainText(directory, "gc.log")));
    }

    @Test
    void detectsGZip() throws IOException {
        assertEquals(LogFileFormat.GZIP, LogFileFormat.of(TestSources.gzip(directory, "gc.log.gz")));
    }

    @Test
    void detectsZip() throws IOException {
        assertEquals(LogFileFormat.ZIP, LogFileFormat.of(TestSources.zip(directory, "gc.log.zip", "gc.log")));
    }

    @Test
    void detectsDirectory() {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormat.of(directory));
    }

    @Test
    void detectsNullPath() {
        assertEquals(LogFileFormat.UNKNOWN, LogFileFormat.of(null));
    }

    @Test
    void magicBytesAreMatchedAgainstTheLeadingBytesOfTheFile() throws IOException {
        Path gzip = TestSources.gzip(directory, "gc.log.gz");
        assertTrue(LogFileFormat.magic(gzip, LogFileFormat.GZIP_MAGIC1, LogFileFormat.GZIP_MAGIC2));
        assertFalse(LogFileFormat.magic(gzip, LogFileFormat.ZIP_MAGIC1, LogFileFormat.ZIP_MAGIC2));
    }

    @Test
    void missingFilesAreReportedAsPlainText() {
        Path missing = directory.resolve("absent.log");
        assertFalse(Files.exists(missing));
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.of(missing));
    }
}

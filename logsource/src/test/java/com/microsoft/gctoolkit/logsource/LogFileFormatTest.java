// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileFormatTest {

    @TempDir
    Path directory;

    @Test
    void detectsPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.detect(LogSourceFixture.writePlainText(directory, "gc.log")));
    }

    @Test
    void detectsGZip() throws IOException {
        assertEquals(LogFileFormat.GZIP, LogFileFormat.detect(LogSourceFixture.writeGZip(directory, "gc.log.gz")));
    }

    @Test
    void detectsZip() throws IOException {
        assertEquals(LogFileFormat.ZIP, LogFileFormat.detect(LogSourceFixture.writeZip(directory, "gc.zip", "gc.log")));
    }

    @Test
    void detectsDirectory() throws IOException {
        Path subdirectory = Files.createDirectory(directory.resolve("rotating"));
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormat.detect(subdirectory));
    }

    @Test
    void reportsUnreadableSourceAsPlainText() {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.detect(directory.resolve("does-not-exist.log")));
    }

    @Test
    void reportsEmptySourceAsPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.detect(Files.createFile(directory.resolve("empty.log"))));
    }
}

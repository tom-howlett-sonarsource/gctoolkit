// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LogFileFormatTest {

    @TempDir
    Path directory;

    @Test
    public void plainTextLogIsDetected() throws IOException {
        Path path = LogSourceTestFiles.plainText(directory, "gc.log", List.of("first", "second"));
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.detect(path));
    }

    @Test
    public void gzipLogIsDetected() throws IOException {
        Path path = LogSourceTestFiles.gzip(directory, "gc.log.gz", List.of("first", "second"));
        assertEquals(LogFileFormat.GZIP, LogFileFormat.detect(path));
    }

    @Test
    public void zipLogIsDetected() throws IOException {
        Path path = LogSourceTestFiles.zip(directory, "gc.log.zip", List.of("gc.log"));
        assertEquals(LogFileFormat.ZIP, LogFileFormat.detect(path));
    }

    @Test
    public void directoryOfLogsIsDetected() throws IOException {
        Path path = Files.createDirectory(directory.resolve("rotating"));
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormat.detect(path));
    }

    @Test
    public void unreadableLogIsReportedAsPlainText() {
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.detect(directory.resolve("missing.log")));
    }

    @Test
    public void emptyLogIsReportedAsPlainText() throws IOException {
        Path path = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.detect(path));
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileSourceTest {

    private static final List<String> LOG_LINES = List.of("first", "second");

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndOpensPlainTextSource() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        Files.write(path, LOG_LINES, StandardCharsets.UTF_8);

        LogFileSource source = LogFileSource.discover(path);

        assertEquals(LogFileSource.Format.PLAIN_TEXT, source.getFormat());
        assertEquals(path, source.getPath());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.stream()) {
            assertEquals(LOG_LINES, lines.collect(toList()));
        }
    }

    @Test
    void discoversAndOpensFirstFileInZipSource() throws IOException {
        Path path = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(String.join("\n", LOG_LINES).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        LogFileSource source = LogFileSource.discover(path);

        assertEquals(LogFileSource.Format.ZIP, source.getFormat());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.stream()) {
            assertEquals(LOG_LINES, lines.collect(toList()));
        }
    }

    @Test
    void discoversAndOpensGzipSource() throws IOException {
        Path path = temporaryDirectory.resolve("gc.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(String.join("\n", LOG_LINES).getBytes(StandardCharsets.UTF_8));
        }

        LogFileSource source = LogFileSource.discover(path);

        assertEquals(LogFileSource.Format.GZIP, source.getFormat());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.stream()) {
            assertEquals(LOG_LINES, lines.collect(toList()));
        }
    }

    @Test
    void discoversDirectorySource() throws IOException {
        LogFileSource source = LogFileSource.discover(temporaryDirectory);

        assertEquals(LogFileSource.Format.DIRECTORY, source.getFormat());
        assertTrue(source.getPath().toFile().isDirectory());
    }
}

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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourceTest {

    private static final String FIRST_LINE = "first line";
    private static final String SECOND_LINE = "second line";

    @TempDir
    Path directory;

    @Test
    void discoversAndReadsPlainSource() throws IOException {
        Path path = directory.resolve("plain.log");
        Files.writeString(path, FIRST_LINE + System.lineSeparator(), StandardCharsets.UTF_8);

        LogSource source = LogSource.discover(path);

        assertEquals(LogSource.Format.PLAIN_TEXT, source.format());
        assertEquals(path, source.path());
        assertEquals(Files.size(path), source.size());
        assertEquals(List.of(FIRST_LINE), read(source.lines()));
    }

    @Test
    void discoversAndReadsGzipSource() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write((FIRST_LINE + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        }

        LogSource source = LogSource.discover(path);

        assertEquals(LogSource.Format.GZIP, source.format());
        assertEquals(Files.size(path), source.size());
        assertEquals(List.of(FIRST_LINE), read(source.lines()));
    }

    @Test
    void discoversEntriesAndReadsZipSources() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            writeEntry(output, "directory/first.log", FIRST_LINE);
            writeEntry(output, "second.log", SECOND_LINE);
        }

        LogSource source = LogSource.discover(path);

        assertEquals(LogSource.Format.ZIP, source.format());
        assertEquals(Files.size(path), source.size());
        assertEquals(List.of("directory/first.log", "second.log"), source.entries());
        assertEquals(List.of(FIRST_LINE), read(source.lines()));
        assertEquals(List.of(SECOND_LINE), read(source.lines("second.log")));
    }

    @Test
    void identifiesDirectoriesButDoesNotOpenThemAsLogs() {
        LogSource source = LogSource.discover(directory);

        assertEquals(LogSource.Format.DIRECTORY, source.format());
        assertThrows(IOException.class, source::open);
    }

    private static List<String> read(java.util.stream.Stream<String> lines) {
        try (java.util.stream.Stream<String> closeable = lines) {
            return closeable.collect(Collectors.toList());
        }
    }

    private static void writeEntry(ZipOutputStream output, String name, String line) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write((line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourcesTest {
    private static final String CONTENT = "line one\nline two\n";
    private static final List<String> LINES = List.of("line one", "line two");

    @TempDir
    Path directory;

    @Test
    void plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT);
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(path));
        assertEquals(LINES, read(path));
    }

    @Test
    void gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogSourceFormat.GZIP, LogSources.formatOf(path));
        assertEquals(LINES, read(path));
    }

    @Test
    void zipSkipsDirectoryEntries() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(LogSourceFormat.ZIP, LogSources.formatOf(path));
        assertEquals(LINES, read(path));
    }

    @Test
    void zipWithoutFileEntryIsRejected() throws IOException {
        Path path = directory.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
        }
        assertThrows(IOException.class, () -> LogSources.open(path));
    }

    @Test
    void directoryIsDiscoveredButNotOpened() {
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.formatOf(directory));
        assertThrows(IOException.class, () -> LogSources.open(directory));
    }

    @Test
    void missingFile() {
        Path path = directory.resolve("missing.log");
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(path));
        assertThrows(IOException.class, () -> LogSources.open(path));
        assertThrows(IOException.class, () -> LogSources.sizeOf(path));
    }

    @Test
    void corruptGzipIsRejected() throws IOException {
        Path path = directory.resolve("bad.gz");
        Files.write(path, new byte[]{0x1F, (byte) 0x8b, 0x00});
        assertThrows(IOException.class, () -> LogSources.open(path));
    }

    @Test
    void sizeInBytes() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT);
        assertEquals(CONTENT.length(), LogSources.sizeOf(path));
    }

    private static List<String> read(Path path) throws IOException {
        try (Stream<String> lines = LogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}

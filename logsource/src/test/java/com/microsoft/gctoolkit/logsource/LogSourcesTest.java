// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {
    private static final String CONTENT = "line one\nline two\n";
    private static final List<String> LINES = List.of("line one", "line two");

    @TempDir
    Path directory;

    @Test
    void discoversFormats() throws IOException {
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.discover(directory));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(plain()));
        assertEquals(LogSourceFormat.GZIP, LogSources.discover(gzip()));
        assertEquals(LogSourceFormat.ZIP, LogSources.discover(zip(true)));
    }

    @Test
    void treatsEmptyAndMissingFilesAsPlainText() throws IOException {
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(empty));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(directory.resolve("missing.log")));
    }

    @Test
    void sizesSourceInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSources.size(plain()));
        assertThrows(IOException.class, () -> LogSources.size(directory.resolve("missing.log")));
    }

    @Test
    void streamsLinesOfEachFormat() throws IOException {
        assertEquals(LINES, lines(plain()));
        assertEquals(LINES, lines(gzip()));
        assertEquals(LINES, lines(zip(true)));
    }

    @Test
    void opensDecompressedContent() throws IOException {
        Path gzip = gzip();
        try (InputStream input = LogSources.open(gzip, LogSources.discover(gzip))) {
            assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8), input.readAllBytes());
        }
    }

    @Test
    void zipWithoutFileEntriesIsEmpty() throws IOException {
        assertTrue(lines(zip(false)).isEmpty());
    }

    @Test
    void directoryCannotBeOpened() {
        assertThrows(IOException.class, () -> LogSources.open(directory, LogSourceFormat.DIRECTORY));
        assertThrows(IOException.class, () -> LogSources.lines(directory, LogSourceFormat.DIRECTORY));
    }

    private static List<String> lines(Path path) throws IOException {
        try (Stream<String> lines = LogSources.lines(path, LogSources.discover(path))) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), CONTENT, StandardCharsets.UTF_8);
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(boolean withFile) throws IOException {
        Path path = directory.resolve(withFile ? "gc.zip" : "dirs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            if (withFile) {
                output.putNextEntry(new ZipEntry("logs/gc.log"));
                output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {

    private static final String CONTENT = "line1\nline2\n";

    @TempDir
    Path directory;

    @Test
    void detectsFormats() throws IOException {
        assertEquals(LogFormat.PLAINTEXT, LogSources.detectFormat(plain("gc.log")));
        assertEquals(LogFormat.GZIP, LogSources.detectFormat(gzip()));
        assertEquals(LogFormat.ZIP, LogSources.detectFormat(zip("a.log", "b.log")));
        assertEquals(LogFormat.DIRECTORY, LogSources.detectFormat(directory));
        assertEquals(LogFormat.PLAINTEXT, LogSources.detectFormat(Files.createFile(directory.resolve("empty"))));
        assertEquals(LogFormat.PLAINTEXT, LogSources.detectFormat(directory.resolve("missing")));
    }

    @Test
    void sizesFilesAndDirectories() throws IOException {
        Path log = plain("gc.log");
        assertEquals(CONTENT.length(), LogSources.size(log));
        Files.createDirectory(directory.resolve("sub"));
        plain("sub/gc.log.1");
        assertEquals(2L * CONTENT.length(), LogSources.size(directory));
    }

    @Test
    void streamsLinesFromEachFormat() throws IOException {
        assertLines(LogSources.lines(plain("gc.log")));
        assertLines(LogSources.lines(gzip()));
        assertLines(LogSources.lines(zip("dir/", "a.log")));
        assertThrows(IOException.class, () -> LogSources.lines(directory));
    }

    @Test
    void zipWithoutFileEntriesIsEmpty() throws IOException {
        try (Stream<String> lines = LogSources.lines(zip("dir/"))) {
            assertEquals(0, lines.count());
        }
    }

    @Test
    void opensNamedZipEntry() throws IOException {
        Path zip = zip("a.log", "b.log");
        assertEquals(List.of("a.log", "b.log"), LogSources.zipEntryNames(zip));
        assertLines(LogSources.zipEntryLines(zip, "b.log"));
        assertThrows(IOException.class, () -> LogSources.zipEntryLines(zip, "missing"));
    }

    @Test
    void openReturnsDecompressedBytes() throws IOException {
        try (InputStream in = LogSources.open(gzip())) {
            assertEquals(CONTENT, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void discoversFiles() throws IOException {
        Path current = plain("gc.log");
        Path rotated = plain("gc.log.1");
        plain("other.log");
        assertEquals(List.of(current, rotated, directory.resolve("other.log")), LogSources.listFiles(directory));
        assertEquals(List.of(current, rotated), LogSources.listFiles(directory, "gc.log"));
    }

    private void assertLines(Stream<String> stream) {
        try (stream) {
            assertEquals(List.of("line1", "line2"), stream.collect(Collectors.toList()));
        }
    }

    private Path plain(String name) throws IOException {
        return Files.writeString(directory.resolve(name), CONTENT, StandardCharsets.UTF_8);
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String... entries) throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/"))
                    out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        assertTrue(Files.exists(path));
        return path;
    }
}

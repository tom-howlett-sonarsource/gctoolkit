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

class LogSourcesTest {

    private static final String CONTENT = "line one\nline two\n";
    private static final List<String> LINES = List.of("line one", "line two");

    @TempDir
    Path directory;

    @Test
    void detectsFormats() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(plain("gc.log")));
        assertEquals(LogSourceFormat.GZIP, LogSources.formatOf(gzip()));
        assertEquals(LogSourceFormat.ZIP, LogSources.formatOf(zip("zipped.zip", "dir/", "a.log", "b.log")));
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.formatOf(directory));
    }

    @Test
    void unreadableOrEmptySourceIsPlainText() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(directory.resolve("missing.log")));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(Files.createFile(directory.resolve("empty.log"))));
    }

    @Test
    void streamsLinesFromEachFormat() throws IOException {
        assertLines(plain("gc.log"));
        assertLines(gzip());
        assertLines(zip("zipped.zip", "dir/", "a.log", "b.log"));
    }

    @Test
    void opensDecompressedContent() throws IOException {
        try (InputStream input = LogSources.open(gzip())) {
            assertArrayEquals(CONTENT.getBytes(StandardCharsets.UTF_8), input.readAllBytes());
        }
    }

    @Test
    void rejectsDirectoriesAndEmptyZips() throws IOException {
        assertThrows(IOException.class, () -> LogSources.open(directory));
        Path emptyZip = zip("empty.zip", "dir/");
        assertThrows(IOException.class, () -> LogSources.lines(emptyZip));
    }

    @Test
    void listsAndStreamsZipEntries() throws IOException {
        Path zip = zip("zipped.zip", "dir/", "a.log", "b.log");
        assertEquals(List.of("a.log", "b.log"), LogSources.zipEntryNames(zip));
        try (Stream<String> lines = LogSources.zipEntryLines(zip, "b.log")) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> LogSources.zipEntryLines(zip, "missing.log"));
    }

    @Test
    void sizesFilesAndDirectories() throws IOException {
        Path log = plain("gc.log");
        assertEquals(CONTENT.length(), LogSources.sizeOf(log));
        plain("gc.log.1");
        Files.createDirectory(directory.resolve("nested"));
        assertEquals(2L * CONTENT.length(), LogSources.sizeOf(directory));
        assertThrows(IOException.class, () -> LogSources.sizeOf(directory.resolve("missing.log")));
    }

    private void assertLines(Path path) throws IOException {
        try (Stream<String> lines = LogSources.lines(path)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    private Path plain(String name) throws IOException {
        return Files.writeString(directory.resolve(name), CONTENT, StandardCharsets.UTF_8);
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String name, String... entries) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entry : entries) {
                output.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/"))
                    output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}

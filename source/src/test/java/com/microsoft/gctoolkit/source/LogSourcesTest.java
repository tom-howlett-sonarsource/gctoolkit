// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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
    void detectsFormats() throws IOException {
        assertEquals(LogFormat.PLAINTEXT, LogSources.formatOf(plain("gc.log")));
        assertEquals(LogFormat.GZIP, LogSources.formatOf(gzip()));
        assertEquals(LogFormat.ZIP, LogSources.formatOf(zip()));
        assertEquals(LogFormat.DIRECTORY, LogSources.formatOf(directory));
        assertEquals(LogFormat.PLAINTEXT, LogSources.formatOf(directory.resolve("missing")));
    }

    @Test
    void readsLinesFromEachFormat() throws IOException {
        assertEquals(LINES, read(LogSources.lines(plain("gc.log"))));
        assertEquals(LINES, read(LogSources.lines(gzip())));
        assertEquals(LINES, read(LogSources.lines(zip())));
    }

    @Test
    void rejectsDirectoryAndMissingSources() {
        assertThrows(IOException.class, () -> LogSources.open(directory));
        assertThrows(IOException.class, () -> LogSources.open(directory.resolve("missing.gz")));
    }

    @Test
    void sizesSourceInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSources.size(plain("gc.log")));
    }

    @Test
    void discoversMatchingFiles() throws IOException {
        Path first = plain("gc.log");
        Path second = plain("gc.log.1");
        plain("other.log");
        List<Path> found = LogSources.discover(directory, name -> name.startsWith("gc.log"));
        assertEquals(List.of(first, second), found.stream().sorted().collect(Collectors.toList()));
    }

    @Test
    void listsAndOpensZipEntries() throws IOException {
        Path zip = zip();
        assertEquals(List.of("gc.log", "gc.log.1"), LogSources.zipEntryNames(zip));
        assertEquals(List.of("second"), read(LogSources.lines(LogSources.openZipEntry(zip, "gc.log.1"))));
        assertThrows(IOException.class, () -> LogSources.openZipEntry(zip, "absent"));
    }

    private static List<String> read(Stream<String> lines) {
        try (lines) {
            return lines.collect(Collectors.toList());
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

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.putNextEntry(new ZipEntry("gc.log.1"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

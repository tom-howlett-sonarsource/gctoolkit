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
    void detectsFormats() throws IOException {
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.format(directory));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.format(plain()));
        assertEquals(LogSourceFormat.GZIP, LogSources.format(gzip()));
        assertEquals(LogSourceFormat.ZIP, LogSources.format(zip()));
    }

    @Test
    void shortAndMissingFilesArePlainText() throws IOException {
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.format(empty));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.format(directory.resolve("missing.log")));
    }

    @Test
    void sizesInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSources.size(plain()));
    }

    @Test
    void listsDirectoryEntries() throws IOException {
        Path plain = plain();
        Path gzip = gzip();
        assertEquals(List.of(plain, gzip), LogSources.list(directory).stream().sorted().collect(Collectors.toList()));
    }

    @Test
    void readsLinesOfEachFormat() throws IOException {
        assertEquals(LINES, read(LogSources.lines(plain(), LogSourceFormat.PLAINTEXT)));
        assertEquals(LINES, read(LogSources.lines(gzip(), LogSourceFormat.GZIP)));
        assertEquals(LINES, read(LogSources.lines(zip(), LogSourceFormat.ZIP)));
    }

    @Test
    void rejectsDirectoryAsLines() {
        assertThrows(IOException.class, () -> LogSources.lines(directory, LogSourceFormat.DIRECTORY));
    }

    @Test
    void readsZipEntries() throws IOException {
        Path zip = zip();
        assertEquals(List.of("first.log", "second.log"), LogSources.zipEntryNames(zip));
        assertEquals(List.of("second"), read(LogSources.zipEntryLines(zip, "second.log")));
        assertThrows(IOException.class, () -> LogSources.zipEntryLines(zip, "absent.log"));
    }

    private static List<String> read(Stream<String> lines) {
        try (lines) {
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

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("dir/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("first.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

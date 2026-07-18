// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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

class LogSourcesTest {

    @TempDir
    Path directory;

    @Test
    void discoversAndReadsPlainFile() throws IOException {
        Path log = write(directory.resolve("gc.log"), "first\nsecond\n");

        List<LogSource> sources = LogSources.discover(log);

        assertEquals(1, sources.size());
        assertEquals(LogSourceFormat.PLAIN_TEXT, sources.get(0).getFormat());
        assertEquals("gc.log", sources.get(0).getName());
        assertEquals(log, sources.get(0).getPath());
        assertEquals("gc.log", LogSources.first(log).getName());
        assertEquals(Files.size(log), sources.get(0).size());
        try (var lines = sources.get(0).lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversFilesInDirectory() throws IOException {
        write(directory.resolve("gc.log.0"), "zero");
        write(directory.resolve("gc.log.1"), "one");
        Files.createDirectory(directory.resolve("ignored"));

        List<LogSource> sources = LogSources.discover(directory);

        assertEquals(List.of("gc.log.0", "gc.log.1"),
                sources.stream().map(LogSource::getName).sorted().collect(Collectors.toList()));
    }

    @Test
    void discoversAndReadsZipEntries() throws IOException {
        Path zip = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/gc.log.0", "zero\n");
            writeEntry(output, "logs/gc.log.1", "one\ntwo\n");
        }

        List<LogSource> sources = LogSources.discover(zip);

        assertEquals(List.of("logs/gc.log.0", "logs/gc.log.1"),
                sources.stream().map(LogSource::getName).collect(Collectors.toList()));
        assertEquals(5, sources.get(0).size());
        assertEquals(8, sources.get(1).size());
        assertEquals("logs/gc.log.1", LogSources.find(zip, "logs/gc.log.1").getName());
        try (var lines = sources.get(1).lines()) {
            assertEquals(List.of("one", "two"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void reportsUncompressedGzipSizeAndReadsLines() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }

        LogSource source = LogSources.discover(gzip).get(0);

        assertEquals(LogSourceFormat.GZIP, source.getFormat());
        assertEquals(content.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsDirectoriesAndMissingZipEntries() throws IOException {
        Path zip = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeEntry(output, "gc.log", "line\n");
        }
        LogSource directorySource = new LogSource(directory, null, LogSourceFormat.DIRECTORY, -1);
        LogSource missingEntry = new LogSource(zip, "missing.log", LogSourceFormat.ZIP, -1);

        assertThrows(IOException.class, directorySource::open);
        assertThrows(IOException.class, missingEntry::open);
        assertThrows(IOException.class, () -> LogSources.find(zip, "missing.log"));
    }

    @Test
    void rejectsEmptyArchives() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(zip))) {
        }

        assertThrows(IOException.class, () -> LogSources.first(zip));
    }

    private Path write(Path path, String content) throws IOException {
        return Files.writeString(path, content);
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

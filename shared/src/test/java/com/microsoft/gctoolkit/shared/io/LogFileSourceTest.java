// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
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

class LogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndOpensPlainFile() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log");
        Files.writeString(log, "first\nsecond\n", StandardCharsets.UTF_8);

        List<LogFileSource> sources = LogFileSources.discover(log);

        assertEquals(1, sources.size());
        assertEquals(LogFileFormat.PLAIN_TEXT, sources.get(0).format());
        assertEquals(log, sources.get(0).path());
        assertEquals("gc.log", sources.get(0).name());
        assertEquals(13L, sources.get(0).size());
        assertEquals(List.of("first", "second"), sources.get(0).lines().collect(Collectors.toList()));
    }

    @Test
    void discoversZipEntriesAndUsesUncompressedSizes() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            addZipEntry(output, "logs/gc.log.1", "one\n");
            addZipEntry(output, "logs/gc.log", "two\nthree\n");
        }

        List<LogFileSource> sources = LogFileSources.discover(archive);

        assertEquals(List.of("logs/gc.log.1", "logs/gc.log"),
                sources.stream().map(LogFileSource::name).collect(Collectors.toList()));
        assertEquals(List.of(4L, 10L), sources.stream().map(LogFileSource::size).collect(Collectors.toList()));
        assertEquals(List.of("two", "three"), sources.get(1).lines().collect(Collectors.toList()));
        assertEquals(List.of("one"), LogFileSources.lines(archive, "logs/gc.log.1").collect(Collectors.toList()));
    }

    @Test
    void opensGzipAndReportsUncompressedSize() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        byte[] content = "compressed\ncontent\n".getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write(content);
        }

        LogFileSource source = LogFileSources.discover(archive).get(0);

        assertEquals(LogFileFormat.GZIP, source.format());
        assertEquals(content.length, source.size());
        assertEquals(List.of("compressed", "content"), source.lines().collect(Collectors.toList()));
    }

    @Test
    void discoversImmediateFilesInDirectory() throws IOException {
        Files.writeString(temporaryDirectory.resolve("gc.log"), "plain");
        Path gzip = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip".getBytes(StandardCharsets.UTF_8));
        }
        Files.createDirectory(temporaryDirectory.resolve("nested"));

        List<LogFileSource> sources = LogFileSources.discover(temporaryDirectory);

        assertEquals(List.of("gc.log", "gc.log.gz"),
                sources.stream().map(LogFileSource::name).collect(Collectors.toList()));
        assertEquals(2, LogFileSources.files(temporaryDirectory).size());
    }

    @Test
    void handlesEmptyAndInvalidSources() throws IOException {
        Path emptyZip = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
        }
        Path plain = temporaryDirectory.resolve("plain.log");
        Files.writeString(plain, "not compressed");

        assertEquals(0L, LogFileSources.lines(emptyZip).count());
        assertThrows(IOException.class, () -> LogFileSources.lines(emptyZip, "missing.log"));
        assertThrows(IOException.class,
                () -> new LogFileSource(temporaryDirectory, LogFileFormat.DIRECTORY, null, 0).open());
        assertThrows(UncheckedIOException.class,
                () -> new LogFileSource(plain, LogFileFormat.GZIP, null, -1).size());
        assertThrows(IOException.class,
                () -> new LogFileSource(emptyZip, LogFileFormat.ZIP, "missing.log", 0).open());
    }

    private static void addZipEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

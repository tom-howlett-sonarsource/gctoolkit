// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    Path directory;

    @Test
    void opensPlainLogAndReportsItsSize() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path log = Files.write(directory.resolve("gc.log"), content);

        LogFileSource source = LogFileSource.from(log);

        assertEquals(LogFileSource.Format.PLAIN_TEXT, source.format());
        assertEquals(content.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensGzipLogAndReportsUncompressedSize() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path log = directory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(log))) {
            output.write(content);
        }

        LogFileSource source = LogFileSource.from(log);

        assertEquals(LogFileSource.Format.GZIP, source.format());
        assertEquals(content.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndOpensEachZipLogEntry() throws IOException {
        Path log = directory.resolve("gc.zip");
        writeZip(log);

        List<LogFileSource> sources = LogFileSource.discover(log);

        assertEquals(List.of("gc.log.1", "gc.log"),
                sources.stream().map(LogFileSource::name).collect(Collectors.toList()));
        assertEquals(4L, sources.get(0).size());
        assertEquals(4L, sources.get(1).size());
        try (var lines = sources.get(1).lines()) {
            assertEquals(List.of("new"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensFirstZipEntryAsPrimarySource() throws IOException {
        Path log = directory.resolve("gc.zip");
        writeZip(log);

        LogFileSource source = LogFileSource.from(log);

        assertEquals(log, source.path());
        assertEquals("gc.log.1", source.name());
        assertEquals(LogFileSource.Format.ZIP, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("old"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversFilesInDirectory() throws IOException {
        Files.writeString(directory.resolve("one.log"), "one");
        Files.createDirectory(directory.resolve("ignored"));
        Files.writeString(directory.resolve("two.log"), "two");

        List<LogFileSource> sources = LogFileSource.discover(directory);

        assertEquals(List.of("one.log", "two.log"),
                sources.stream().map(LogFileSource::name).sorted().collect(Collectors.toList()));
        assertEquals(List.of("ignored", "one.log", "two.log"), LogFileSource.children(directory).stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList()));
    }

    @Test
    void directorySourceHasNoByteContent() throws IOException {
        LogFileSource source = LogFileSource.from(directory);

        assertEquals(LogFileSource.Format.DIRECTORY, source.format());
        assertEquals(0L, source.size());
        assertThrows(IOException.class, source::lines);
    }

    private static void writeZip(Path path) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.1"));
            output.write("old\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write("new\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}

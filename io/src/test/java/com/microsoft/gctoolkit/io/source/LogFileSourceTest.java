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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndReadsPlainTextByContent() throws IOException {
        Path path = temporaryDirectory.resolve("plain.zip");
        Files.writeString(path, "first\nsecond\n");

        LogFileSource source = LogFileSource.from(path);

        assertEquals(LogFileFormat.PLAIN_TEXT, source.format());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndReadsFirstZipFileEntry() throws IOException {
        Path path = temporaryDirectory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip line\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        LogFileSource source = LogFileSource.from(path);

        assertEquals(LogFileFormat.ZIP, source.format());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("zip line"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndReadsGzipByContent() throws IOException {
        Path path = temporaryDirectory.resolve("compressed.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("gzip line\n".getBytes(StandardCharsets.UTF_8));
        }

        LogFileSource source = LogFileSource.from(path);

        assertEquals(LogFileFormat.GZIP, source.format());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("gzip line"), lines.collect(toList()));
        }
    }

    @Test
    void rejectsDirectoriesAsLineSources() {
        LogFileSource source = LogFileSource.from(temporaryDirectory);

        assertEquals(LogFileFormat.DIRECTORY, source.format());
        assertThrows(IOException.class, source::lines);
    }
}

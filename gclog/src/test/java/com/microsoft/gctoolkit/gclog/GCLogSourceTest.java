// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
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

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndReadsPlainText() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        Files.writeString(path, "first\nsecond\n", StandardCharsets.UTF_8);

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (InputStream input = source.open()) {
            assertEquals("first\nsecond\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndReadsGzipByMagicBytes() throws IOException {
        Path path = temporaryDirectory.resolve("gc.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("gzip line\n".getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Format.GZIP, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (var lines = source.lines()) {
            assertEquals(List.of("gzip line"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndReadsFirstZipFileEntry() throws IOException {
        Path path = temporaryDirectory.resolve("gc.data");
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

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Format.ZIP, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (var lines = source.lines()) {
            assertEquals(List.of("zip line"), lines.collect(toList()));
        }
    }

    @Test
    void discoversDirectoryAndRejectsOpeningIt() throws IOException {
        GCLogSource source = GCLogSource.from(temporaryDirectory);

        assertEquals(GCLogSource.Format.DIRECTORY, source.format());
        assertThrows(IOException.class, source::open);
    }

    @Test
    void readsZipWithoutFileEntriesAsEmpty() throws IOException {
        Path path = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        try (var lines = GCLogSource.from(path).lines()) {
            assertEquals(List.of(), lines.collect(toList()));
        }
    }

    @Test
    void treatsUnavailableSourceAsPlainTextUntilOpened() {
        Path path = temporaryDirectory.resolve("missing.log");

        GCLogSource source = GCLogSource.from(path);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, source.format());
        assertThrows(IOException.class, source::open);
    }
}

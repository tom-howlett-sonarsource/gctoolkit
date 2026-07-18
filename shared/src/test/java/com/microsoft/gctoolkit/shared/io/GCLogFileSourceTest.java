// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileSourceTest {

    @TempDir
    Path directory;

    @Test
    void opensPlainTextAndReportsPhysicalSize() throws IOException {
        Path log = Files.writeString(directory.resolve("gc.log"), "first\nsecond\n");
        GCLogFileSource source = new GCLogFileSource(log);

        assertEquals(GCLogFileSource.Format.PLAIN_TEXT, source.format());
        assertEquals(Files.size(log), source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensFirstFileEntryInZip() throws IOException {
        Path log = directory.resolve("gc.zip");
        try (OutputStream output = Files.newOutputStream(log); ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("logs/gc.log"));
            zip.write("zip line\n".getBytes());
            zip.closeEntry();
        }

        GCLogFileSource source = new GCLogFileSource(log);

        assertEquals(GCLogFileSource.Format.ZIP, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("zip line"), lines.collect(Collectors.toList()));
        }
        try (var entries = source.zipEntries()) {
            assertEquals(List.of("logs/gc.log"), entries.collect(Collectors.toList()));
        }
        try (var lines = source.lines("logs/gc.log")) {
            assertEquals(List.of("zip line"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> source.lines("logs/"));
        assertThrows(IOException.class, () -> source.lines("missing.log"));
    }

    @Test
    void opensGzip() throws IOException {
        Path log = directory.resolve("gc.gz");
        try (OutputStream output = Files.newOutputStream(log); GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write("gzip line\n".getBytes());
        }

        GCLogFileSource source = new GCLogFileSource(log);

        assertEquals(GCLogFileSource.Format.GZIP, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("gzip line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversDirectoryEntriesAndDirectoryFormat() throws IOException {
        Path first = Files.writeString(directory.resolve("first.log"), "first");
        Path second = Files.writeString(directory.resolve("second.log"), "second");
        GCLogFileSource source = new GCLogFileSource(directory);

        assertEquals(GCLogFileSource.Format.DIRECTORY, source.format());
        try (var entries = source.entries()) {
            List<Path> discovered = entries.sorted().collect(Collectors.toList());
            assertEquals(List.of(first, second), discovered);
        }
    }

    @Test
    void identifiesUnknownForMissingPath() throws IOException {
        GCLogFileSource source = new GCLogFileSource(directory.resolve("missing.log"));

        assertEquals(GCLogFileSource.Format.UNKNOWN, source.format());
        assertFalse(source.isPlainText());
        assertFalse(source.isZip());
        assertFalse(source.isGZip());
        assertFalse(source.isDirectory());
        assertTrue(source.path().endsWith("missing.log"));
        assertEquals(List.of(), source.entries().collect(Collectors.toList()));
        assertThrows(IOException.class, source::lines);
    }

    @Test
    void discoversRegularFileAsSingleEntry() throws IOException {
        Path log = Files.writeString(directory.resolve("gc.log"), "line");
        GCLogFileSource source = new GCLogFileSource(log);

        try (var entries = source.entries()) {
            assertEquals(List.of(log), entries.collect(Collectors.toList()));
        }
    }
}

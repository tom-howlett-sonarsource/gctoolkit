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
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> LINES = List.of("first", "second", "third");

    @Test
    void detectMissingPathReportsUnknown() {
        assertEquals(GCLogSourceFormat.UNKNOWN, GCLogSources.detect(null));
        assertEquals(GCLogSourceFormat.UNKNOWN, GCLogSources.detect(Path.of("does-not-exist-" + System.nanoTime())));
    }

    @Test
    void detectClassifiesPlainDirectoryZipAndGZip(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp.resolve("plain.log"));
        Path zip = writeZip(tmp.resolve("plain.log.zip"), "plain.log");
        Path gzip = writeGZip(tmp.resolve("plain.log.gz"));
        Path dir = Files.createDirectory(tmp.resolve("rotating"));

        assertEquals(GCLogSourceFormat.PLAINTEXT, GCLogSources.detect(plain));
        assertEquals(GCLogSourceFormat.ZIP, GCLogSources.detect(zip));
        assertEquals(GCLogSourceFormat.GZIP, GCLogSources.detect(gzip));
        assertEquals(GCLogSourceFormat.DIRECTORY, GCLogSources.detect(dir));
    }

    @Test
    void detectHandlesEmptyFileAsUnknown(@TempDir Path tmp) throws IOException {
        Path empty = Files.createFile(tmp.resolve("empty.log"));
        assertEquals(GCLogSourceFormat.UNKNOWN, GCLogSources.detect(empty));
    }

    @Test
    void sizeInBytesReturnsRegularFileSize(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp.resolve("size.log"));
        assertEquals(Files.size(plain), GCLogSources.sizeInBytes(plain));
    }

    @Test
    void sizeInBytesReturnsMinusOneForDirectoryAndMissingAndNull(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("dir"));
        assertEquals(-1L, GCLogSources.sizeInBytes(dir));
        assertEquals(-1L, GCLogSources.sizeInBytes(tmp.resolve("missing")));
        assertEquals(-1L, GCLogSources.sizeInBytes(null));
    }

    @Test
    void openStreamReadsPlainZipAndGZipContent(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp.resolve("stream.log"));
        Path zip = writeZip(tmp.resolve("stream.log.zip"), "stream.log");
        Path gzip = writeGZip(tmp.resolve("stream.log.gz"));

        try (Stream<String> s = GCLogSources.openStream(plain, GCLogSourceFormat.PLAINTEXT)) {
            assertEquals(LINES, s.collect(Collectors.toList()));
        }
        try (Stream<String> s = GCLogSources.openStream(zip, GCLogSourceFormat.ZIP)) {
            assertEquals(LINES, s.collect(Collectors.toList()));
        }
        try (Stream<String> s = GCLogSources.openStream(gzip, GCLogSourceFormat.GZIP)) {
            assertEquals(LINES, s.collect(Collectors.toList()));
        }
    }

    @Test
    void openStreamRejectsUnsupportedFormats(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp.resolve("only.log"));
        assertThrows(IOException.class, () -> GCLogSources.openStream(plain, null));
        assertThrows(IOException.class, () -> GCLogSources.openStream(plain, GCLogSourceFormat.UNKNOWN));
        assertThrows(IOException.class, () -> GCLogSources.openStream(plain, GCLogSourceFormat.DIRECTORY));
    }

    @Test
    void openZipFirstEntrySkipsDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("with-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("nested/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("nested/inside.log"));
            out.write("only-line\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> s = GCLogSources.openZipFirstEntry(zip)) {
            List<String> collected = s.collect(Collectors.toList());
            assertEquals(1, collected.size());
            assertEquals("only-line", collected.get(0));
        }
    }

    @Test
    void openZipFirstEntryThrowsForEmptyArchive(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            // no entries
        }
        IOException ex = assertThrows(IOException.class, () -> GCLogSources.openZipFirstEntry(zip));
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("No file entries"));
    }

    private static Path writePlain(Path file) throws IOException {
        Files.write(file, LINES);
        return file;
    }

    private static Path writeZip(Path file, String entryName) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry(entryName));
            for (String line : LINES) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            out.closeEntry();
        }
        return file;
    }

    private static Path writeGZip(Path file) throws IOException {
        try (OutputStream raw = Files.newOutputStream(file);
             GZIPOutputStream out = new GZIPOutputStream(raw)) {
            for (String line : LINES) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return file;
    }
}

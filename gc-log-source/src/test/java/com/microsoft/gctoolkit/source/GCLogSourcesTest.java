// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> LINES = List.of("line one", "line two", "line three");

    @Test
    void openPlainTextReadsAllLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.log");
        Files.write(file, LINES);
        try (Stream<String> stream = GCLogSources.openPlainText(file)) {
            assertEquals(LINES, stream.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    void openZipReadsFirstEntry(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("logs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("nested/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write(String.join("\n", LINES).getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(LINES, stream.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    void openZipEmptyArchiveReturnsEmpty(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            // no entries
        }
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(0L, stream.count());
        }
    }

    @Test
    void openGZipReadsDecompressedContent(@TempDir Path dir) throws IOException {
        Path gz = dir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(gz))) {
            gos.write(String.join("\n", LINES).getBytes());
        }
        try (Stream<String> stream = GCLogSources.openGZip(gz)) {
            assertEquals(LINES, stream.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    void byteSizeMatchesWrittenBytes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("size.log");
        byte[] payload = "abcdef".getBytes();
        Files.write(file, payload);
        assertEquals(payload.length, GCLogSources.byteSize(file));
    }

    @Test
    void listSourcesEnumeratesDirectoryEntries(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("a.log"));
        Files.createFile(dir.resolve("b.log"));
        Files.createDirectory(dir.resolve("nested"));
        try (Stream<Path> entries = GCLogSources.listSources(dir)) {
            long count = entries.count();
            assertEquals(3L, count);
        }
    }

    @Test
    void listSourcesReturnsPathsUnderDirectory(@TempDir Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("only.log"));
        try (Stream<Path> entries = GCLogSources.listSources(dir)) {
            assertTrue(entries.anyMatch(p -> p.equals(file)));
        }
    }
}

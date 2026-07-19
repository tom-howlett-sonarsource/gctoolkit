// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> LINES = List.of("first", "second", "third");

    @Test
    void plainTextRoundTrip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("gc.log");
        Files.write(file, LINES);

        assertFalse(GCLogSources.isGZip(file));
        assertFalse(GCLogSources.isZip(file));

        try (Stream<String> lines = GCLogSources.openPlain(file)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void gzipRoundTrip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("gc.log.gz");
        try (OutputStream fileOut = Files.newOutputStream(file);
             GZIPOutputStream gz = new GZIPOutputStream(fileOut)) {
            for (String line : LINES) {
                gz.write((line + "\n").getBytes());
            }
        }

        assertTrue(GCLogSources.isGZip(file));
        assertFalse(GCLogSources.isZip(file));

        try (Stream<String> lines = GCLogSources.openGZip(file)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipRoundTripSkipsDirectoryEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("gc.log.zip");
        try (OutputStream fileOut = Files.newOutputStream(file);
             ZipOutputStream zip = new ZipOutputStream(fileOut)) {
            ZipEntry directory = new ZipEntry("logs/");
            zip.putNextEntry(directory);
            zip.closeEntry();

            ZipEntry entry = new ZipEntry("logs/gc.log");
            zip.putNextEntry(entry);
            for (String line : LINES) {
                zip.write((line + "\n").getBytes());
            }
            zip.closeEntry();
        }

        assertTrue(GCLogSources.isZip(file));
        assertFalse(GCLogSources.isGZip(file));

        try (Stream<String> lines = GCLogSources.openZip(file)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void matchesMagicReturnsFalseForMissingFile(@TempDir Path dir) {
        Path missing = dir.resolve("missing");
        assertFalse(GCLogSources.matchesMagic(missing, 0x1F, 0x8B));
    }

    @Test
    void listSourcesEnumeratesDirectoryEntries(@TempDir Path dir) throws IOException {
        Path a = Files.createFile(dir.resolve("a.log"));
        Path b = Files.createFile(dir.resolve("b.log"));

        try (Stream<Path> entries = GCLogSources.listSources(dir)) {
            List<Path> collected = entries.collect(Collectors.toList());
            assertEquals(2, collected.size());
            assertTrue(collected.contains(a));
            assertTrue(collected.contains(b));
        }
    }

    @Test
    void magicConstantsAreExposed() {
        assertNotNull(GCLogSources.class);
        assertEquals(0x1F, GCLogSources.GZIP_MAGIC1);
        assertEquals(0x8b, GCLogSources.GZIP_MAGIC2);
        assertEquals(0x50, GCLogSources.ZIP_MAGIC1);
        assertEquals(0x4b, GCLogSources.ZIP_MAGIC2);
    }
}

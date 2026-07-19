// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.common;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");

    @Test
    void detectFormatReturnsPlainTextForRegularFile(@TempDir Path dir) throws IOException {
        Path plain = writePlain(dir.resolve("gc.log"));
        assertEquals(SourceFormat.PLAINTEXT, GCLogSource.detectFormat(plain));
    }

    @Test
    void detectFormatReturnsGzipForGzipMagic(@TempDir Path dir) throws IOException {
        Path gz = writeGZip(dir.resolve("gc.log.gz"));
        assertEquals(SourceFormat.GZIP, GCLogSource.detectFormat(gz));
    }

    @Test
    void detectFormatReturnsZipForZipMagic(@TempDir Path dir) throws IOException {
        Path zip = writeZip(dir.resolve("gc.log.zip"));
        assertEquals(SourceFormat.ZIP, GCLogSource.detectFormat(zip));
    }

    @Test
    void detectFormatReturnsDirectoryForDirectory(@TempDir Path dir) {
        assertEquals(SourceFormat.DIRECTORY, GCLogSource.detectFormat(dir));
    }

    @Test
    void detectFormatReturnsUnknownForMissingFile(@TempDir Path dir) {
        assertEquals(SourceFormat.UNKNOWN, GCLogSource.detectFormat(dir.resolve("does-not-exist")));
    }

    @Test
    void detectFormatReturnsUnknownForNullPath() {
        assertEquals(SourceFormat.UNKNOWN, GCLogSource.detectFormat(null));
    }

    @Test
    void sizeInBytesReportsFileSize(@TempDir Path dir) throws IOException {
        Path plain = writePlain(dir.resolve("gc.log"));
        long expected = Files.size(plain);
        assertTrue(expected > 0);
        assertEquals(expected, GCLogSource.sizeInBytes(plain));
    }

    @Test
    void sizeInBytesReturnsMinusOneWhenPathIsMissing(@TempDir Path dir) {
        assertEquals(-1L, GCLogSource.sizeInBytes(dir.resolve("missing")));
    }

    @Test
    void sizeInBytesReturnsMinusOneForNullPath() {
        assertEquals(-1L, GCLogSource.sizeInBytes(null));
    }

    @Test
    void openPlainStreamsLines(@TempDir Path dir) throws IOException {
        Path plain = writePlain(dir.resolve("gc.log"));
        try (Stream<String> stream = GCLogSource.openPlain(plain)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipStreamsLinesFromFirstEntry(@TempDir Path dir) throws IOException {
        Path zip = writeZip(dir.resolve("gc.log.zip"));
        try (Stream<String> stream = GCLogSource.openZip(zip)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsDirectoryEntries(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("with-dir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("nested/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        try (Stream<String> stream = GCLogSource.openZip(zip)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipThrowsForMissingFile(@TempDir Path dir) {
        assertThrows(IOException.class, () -> GCLogSource.openZip(dir.resolve("missing.zip")));
    }

    @Test
    void openGZipStreamsLines(@TempDir Path dir) throws IOException {
        Path gz = writeGZip(dir.resolve("gc.log.gz"));
        try (Stream<String> stream = GCLogSource.openGZip(gz)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipThrowsForNonGzipContent(@TempDir Path dir) throws IOException {
        Path plain = writePlain(dir.resolve("gc.log"));
        assertThrows(IOException.class, () -> GCLogSource.openGZip(plain));
    }

    @Test
    void openGZipThrowsForMissingFile(@TempDir Path dir) {
        assertThrows(IOException.class, () -> GCLogSource.openGZip(dir.resolve("missing.gz")));
    }

    @Test
    void sourceFormatEnumCoversExpectedValues() {
        // guard against accidental removal / rename
        assertTrue(SourceFormat.valueOf("PLAINTEXT") == SourceFormat.PLAINTEXT);
        assertTrue(SourceFormat.valueOf("ZIP") == SourceFormat.ZIP);
        assertTrue(SourceFormat.valueOf("GZIP") == SourceFormat.GZIP);
        assertTrue(SourceFormat.valueOf("DIRECTORY") == SourceFormat.DIRECTORY);
        assertTrue(SourceFormat.valueOf("UNKNOWN") == SourceFormat.UNKNOWN);
        assertFalse(SourceFormat.PLAINTEXT == SourceFormat.ZIP);
    }

    private static Path writePlain(Path path) throws IOException {
        Files.write(path, LINES, StandardCharsets.UTF_8);
        return path;
    }

    private static Path writeGZip(Path path) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private static Path writeZip(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return path;
    }
}

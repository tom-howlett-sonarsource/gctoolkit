// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceStreamsTest {

    private static final String LINE_1 = "2024-01-01T00:00:00.000+0000: [GC pause]";
    private static final String LINE_2 = "2024-01-01T00:00:01.000+0000: [GC cleanup]";

    // ---- format detection ----

    @Test
    void detectPlainTextFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gc.log");
        Files.write(file, List.of(LINE_1, LINE_2));
        assertEquals(GCLogSourceStreams.FileFormat.PLAINTEXT, GCLogSourceStreams.detectFormat(file));
    }

    @Test
    void detectGzipFile(@TempDir Path tmp) throws IOException {
        Path file = writeGzipFile(tmp, "gc.log.gz", LINE_1, LINE_2);
        assertEquals(GCLogSourceStreams.FileFormat.GZIP, GCLogSourceStreams.detectFormat(file));
    }

    @Test
    void detectZipFile(@TempDir Path tmp) throws IOException {
        Path file = writeZipFile(tmp, "gc.log.zip", "gc.log", LINE_1, LINE_2);
        assertEquals(GCLogSourceStreams.FileFormat.ZIP, GCLogSourceStreams.detectFormat(file));
    }

    @Test
    void detectDirectory(@TempDir Path tmp) {
        assertEquals(GCLogSourceStreams.FileFormat.DIRECTORY, GCLogSourceStreams.detectFormat(tmp));
    }

    @Test
    void detectNonExistentFileReturnsPlainText(@TempDir Path tmp) {
        Path missing = tmp.resolve("no-such-file.log");
        // Non-existent file cannot match magic bytes, so falls through to PLAINTEXT
        assertEquals(GCLogSourceStreams.FileFormat.PLAINTEXT, GCLogSourceStreams.detectFormat(missing));
    }

    // ---- plain-text streaming ----

    @Test
    void streamPlainTextReturnsAllLines(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gc.log");
        Files.write(file, List.of(LINE_1, LINE_2));

        try (Stream<String> stream = GCLogSourceStreams.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    // ---- GZIP streaming ----

    @Test
    void streamGZipFileReturnsAllLines(@TempDir Path tmp) throws IOException {
        Path file = writeGzipFile(tmp, "gc.log.gz", LINE_1, LINE_2);

        try (Stream<String> stream = GCLogSourceStreams.streamGZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    // ---- ZIP streaming ----

    @Test
    void streamZipFileReturnsAllLines(@TempDir Path tmp) throws IOException {
        Path file = writeZipFile(tmp, "gc.log.zip", "gc.log", LINE_1, LINE_2);

        try (Stream<String> stream = GCLogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            // Add a directory entry first
            ZipEntry dirEntry = new ZipEntry("logs/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
            // Then the actual log file
            ZipEntry logEntry = new ZipEntry("logs/gc.log");
            zos.putNextEntry(logEntry);
            byte[] content = (LINE_1 + "\n" + LINE_2 + "\n").getBytes(StandardCharsets.UTF_8);
            zos.write(content);
            zos.closeEntry();
        }

        try (Stream<String> stream = GCLogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    // ---- auto-detecting stream ----

    @Test
    void streamAutoDetectsPlainText(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gc.log");
        Files.write(file, List.of(LINE_1, LINE_2));

        try (Stream<String> stream = GCLogSourceStreams.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void streamAutoDetectsGzip(@TempDir Path tmp) throws IOException {
        Path file = writeGzipFile(tmp, "gc.log.gz", LINE_1, LINE_2);

        try (Stream<String> stream = GCLogSourceStreams.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void streamAutoDetectsZip(@TempDir Path tmp) throws IOException {
        Path file = writeZipFile(tmp, "gc.log.zip", "gc.log", LINE_1, LINE_2);

        try (Stream<String> stream = GCLogSourceStreams.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void streamThrowsForDirectory(@TempDir Path tmp) {
        assertThrows(IOException.class, () -> GCLogSourceStreams.stream(tmp));
    }

    // ---- byte sizing ----

    @Test
    void byteSizeReturnsFileLength(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gc.log");
        byte[] content = (LINE_1 + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);

        assertEquals(content.length, GCLogSourceStreams.byteSize(file));
    }

    @Test
    void byteSizeThrowsForMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("no-such-file.log");
        assertThrows(IOException.class, () -> GCLogSourceStreams.byteSize(missing));
    }

    // ---- helpers ----

    private static Path writeGzipFile(Path dir, String name, String... lines) throws IOException {
        Path file = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(file);
             GZIPOutputStream gzos = new GZIPOutputStream(os)) {
            for (String line : lines) {
                gzos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        return file;
    }

    private static Path writeZipFile(Path dir, String zipName, String entryName, String... lines) throws IOException {
        Path file = dir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            for (String line : lines) {
                zos.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
            zos.closeEntry();
        }
        return file;
    }
}

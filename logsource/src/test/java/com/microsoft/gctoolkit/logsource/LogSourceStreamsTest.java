// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceStreamsTest {

    private static final String LINE1 = "2023-01-01T00:00:00.000+0000 GC(0) Pause Young";
    private static final String LINE2 = "2023-01-01T00:00:01.000+0000 GC(1) Pause Full";
    private static final String LOG_CONTENT = LINE1 + "\n" + LINE2 + "\n";

    // --- detectFormat ---

    @Test
    void detectFormatPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, LOG_CONTENT);
        assertEquals(FileFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatDirectory(@TempDir Path tempDir) {
        assertEquals(FileFormat.DIRECTORY, LogSourceStreams.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip(@TempDir Path tempDir) throws IOException {
        Path file = writeZip(tempDir, "gc.log.zip", "gc.log", LOG_CONTENT);
        assertEquals(FileFormat.ZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatGZip(@TempDir Path tempDir) throws IOException {
        Path file = writeGZip(tempDir, "gc.log.gz", LOG_CONTENT);
        assertEquals(FileFormat.GZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty");
        Files.createFile(file);
        // An empty file cannot match ZIP or GZIP magic, defaults to PLAINTEXT
        assertEquals(FileFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    // --- byteSize ---

    @Test
    void byteSizeReturnsFileSize(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log");
        byte[] bytes = LOG_CONTENT.getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);
        assertEquals(bytes.length, LogSourceStreams.byteSize(file));
    }

    @Test
    void byteSizeThrowsForMissingFile(@TempDir Path tempDir) {
        Path file = tempDir.resolve("no-such-file");
        assertThrows(IOException.class, () -> LogSourceStreams.byteSize(file));
    }

    // --- lines (plain text) ---

    @Test
    void linesReadsPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.lines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
            assertEquals(LINE2, lines.get(1));
        }
    }

    // --- linesFromZip ---

    @Test
    void linesFromZipReadsFirstEntry(@TempDir Path tempDir) throws IOException {
        Path file = writeZip(tempDir, "gc.log.zip", "gc.log", LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.linesFromZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
            assertEquals(LINE2, lines.get(1));
        }
    }

    @Test
    void linesFromZipSkipsDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (OutputStream fos = Files.newOutputStream(file);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            // directory entry
            ZipEntry dirEntry = new ZipEntry("logs/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();
            // file entry
            ZipEntry fileEntry = new ZipEntry("logs/gc.log");
            zos.putNextEntry(fileEntry);
            zos.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceStreams.linesFromZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
        }
    }

    // --- linesFromGZip ---

    @Test
    void linesFromGZipReadsContent(@TempDir Path tempDir) throws IOException {
        Path file = writeGZip(tempDir, "gc.log.gz", LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.linesFromGZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
            assertEquals(LINE2, lines.get(1));
        }
    }

    // --- stream (auto-detect) ---

    @Test
    void streamAutoDetectsPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
        }
    }

    @Test
    void streamAutoDetectsZip(@TempDir Path tempDir) throws IOException {
        Path file = writeZip(tempDir, "gc.log.zip", "gc.log", LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
        }
    }

    @Test
    void streamAutoDetectsGZip(@TempDir Path tempDir) throws IOException {
        Path file = writeGZip(tempDir, "gc.log.gz", LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
        }
    }

    @Test
    void streamThrowsForDirectory(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> LogSourceStreams.stream(tempDir));
    }

    // --- stream with explicit format ---

    @Test
    void streamWithExplicitFormatPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, LOG_CONTENT);
        try (Stream<String> stream = LogSourceStreams.stream(file, FileFormat.PLAINTEXT)) {
            assertEquals(2, stream.collect(Collectors.toList()).size());
        }
    }

    @Test
    void streamThrowsForUnknownFormat(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, LOG_CONTENT);
        assertThrows(IOException.class, () -> LogSourceStreams.stream(file, FileFormat.UNKNOWN));
    }

    @Test
    void streamThrowsForDirectoryFormat(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> LogSourceStreams.stream(tempDir, FileFormat.DIRECTORY));
    }

    // --- FileFormat enum ---

    @Test
    void fileFormatEnumValues() {
        FileFormat[] values = FileFormat.values();
        assertEquals(5, values.length);
        assertEquals(FileFormat.ZIP, FileFormat.valueOf("ZIP"));
        assertEquals(FileFormat.GZIP, FileFormat.valueOf("GZIP"));
        assertEquals(FileFormat.PLAINTEXT, FileFormat.valueOf("PLAINTEXT"));
        assertEquals(FileFormat.DIRECTORY, FileFormat.valueOf("DIRECTORY"));
        assertEquals(FileFormat.UNKNOWN, FileFormat.valueOf("UNKNOWN"));
    }

    // --- helpers ---

    private Path writeZip(Path dir, String zipName, String entryName, String content) throws IOException {
        Path file = dir.resolve(zipName);
        try (OutputStream fos = Files.newOutputStream(file);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return file;
    }

    private Path writeGZip(Path dir, String gzipName, String content) throws IOException {
        Path file = dir.resolve(gzipName);
        try (OutputStream fos = Files.newOutputStream(file);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            gzos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}

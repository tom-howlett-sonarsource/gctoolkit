// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

class LogStreamSourceTest {

    @TempDir
    Path tempDir;

    private static final String GC_LOG = "gc.log";
    private static final String LINE_1 = "[0.011s][info][gc] Using G1";
    private static final String LINE_2 = "[0.012s][info][gc] Heap region size: 1M";

    @Test
    void detectFormatPlainText() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n" + LINE_2 + "\n");

        assertEquals(LogStreamSource.FileFormat.PLAINTEXT, LogStreamSource.detectFormat(plain));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(LogStreamSource.FileFormat.DIRECTORY, LogStreamSource.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path zip = createZipFile();

        assertEquals(LogStreamSource.FileFormat.ZIP, LogStreamSource.detectFormat(zip));
    }

    @Test
    void detectFormatGZip() throws IOException {
        Path gzip = createGZipFile();

        assertEquals(LogStreamSource.FileFormat.GZIP, LogStreamSource.detectFormat(gzip));
    }

    @Test
    void matchesMagicReturnsFalseForNonExistentFile() {
        Path noFile = tempDir.resolve("does-not-exist.log");
        assertFalse(LogStreamSource.matchesMagic(noFile, 0x50, 0x4b));
    }

    @Test
    void sizeInBytesReturnsFileSize() throws IOException {
        Path plain = tempDir.resolve("sized.log");
        String content = "hello\n";
        Files.writeString(plain, content);

        assertEquals(content.getBytes().length, LogStreamSource.sizeInBytes(plain));
    }

    @Test
    void sizeInBytesReturnsMinusOneForMissingFile() {
        Path noFile = tempDir.resolve("missing.log");
        assertEquals(-1L, LogStreamSource.sizeInBytes(noFile));
    }

    @Test
    void streamPlainTextFile() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n" + LINE_2 + "\n");

        try (Stream<String> lines = LogStreamSource.stream(plain)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(2, result.size());
            assertEquals(LINE_1, result.get(0));
            assertEquals(LINE_2, result.get(1));
        }
    }

    @Test
    void streamZipFile() throws IOException {
        Path zip = createZipFile();

        try (Stream<String> lines = LogStreamSource.streamZipFile(zip)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(2, result.size());
            assertEquals(LINE_1, result.get(0));
            assertEquals(LINE_2, result.get(1));
        }
    }

    @Test
    void streamGZipFile() throws IOException {
        Path gzip = createGZipFile();

        try (Stream<String> lines = LogStreamSource.streamGZipFile(gzip)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(2, result.size());
            assertEquals(LINE_1, result.get(0));
            assertEquals(LINE_2, result.get(1));
        }
    }

    @Test
    void streamWithPreDetectedFormat() throws IOException {
        Path plain = tempDir.resolve("predetected.log");
        Files.writeString(plain, LINE_1 + "\n");

        try (Stream<String> lines = LogStreamSource.stream(plain, LogStreamSource.FileFormat.PLAINTEXT)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(1, result.size());
            assertEquals(LINE_1, result.get(0));
        }
    }

    @Test
    void streamThrowsForDirectoryFormat() {
        assertThrows(IOException.class,
                () -> LogStreamSource.stream(tempDir, LogStreamSource.FileFormat.DIRECTORY));
    }

    @Test
    void streamThrowsForUnknownFormat() {
        assertThrows(IOException.class,
                () -> LogStreamSource.stream(tempDir, LogStreamSource.FileFormat.UNKNOWN));
    }

    @Test
    void streamZipSkipsDirectoryEntries() throws IOException {
        Path zip = tempDir.resolve("with-dir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> lines = LogStreamSource.streamZipFile(zip)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(1, result.size());
            assertEquals(LINE_1, result.get(0));
        }
    }

    private Path createZipFile() throws IOException {
        Path zip = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(GC_LOG));
            zos.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes());
            zos.closeEntry();
        }
        return zip;
    }

    private Path createGZipFile() throws IOException {
        Path gzip = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            gzos.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes());
        }
        return gzip;
    }
}

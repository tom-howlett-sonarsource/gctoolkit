// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.logsource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogStreamSourceTest {

    @TempDir
    Path tempDir;

    private static final List<String> LOG_LINES = List.of(
            "[0.003s][info][gc] Using G1",
            "[0.004s][info][gc] Heap region size: 1M",
            "[0.010s][info][gc] GC(0) Pause Young (Normal)"
    );

    private Path plainFile;
    private Path zipFile;
    private Path gzipFile;

    @BeforeEach
    void setUp() throws IOException {
        plainFile = tempDir.resolve("gc.log");
        Files.write(plainFile, LOG_LINES);

        zipFile = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            for (String line : LOG_LINES) {
                zos.write((line + "\n").getBytes());
            }
            zos.closeEntry();
        }

        gzipFile = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzipFile.toFile()))) {
            for (String line : LOG_LINES) {
                gos.write((line + "\n").getBytes());
            }
        }
    }

    @Test
    void detectFormatPlainText() {
        assertEquals(LogSourceFormat.PLAIN_TEXT, LogStreamSource.detectFormat(plainFile));
    }

    @Test
    void detectFormatZip() {
        assertEquals(LogSourceFormat.ZIP, LogStreamSource.detectFormat(zipFile));
    }

    @Test
    void detectFormatGZip() {
        assertEquals(LogSourceFormat.GZIP, LogStreamSource.detectFormat(gzipFile));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(LogSourceFormat.DIRECTORY, LogStreamSource.detectFormat(tempDir));
    }

    @Test
    void linesFromPlainText() throws IOException {
        try (Stream<String> stream = LogStreamSource.lines(plainFile)) {
            List<String> result = stream.collect(Collectors.toList());
            assertEquals(LOG_LINES, result);
        }
    }

    @Test
    void linesFromZip() throws IOException {
        try (Stream<String> stream = LogStreamSource.linesFromZip(zipFile)) {
            List<String> result = stream.collect(Collectors.toList());
            assertEquals(LOG_LINES, result);
        }
    }

    @Test
    void linesFromGZip() throws IOException {
        try (Stream<String> stream = LogStreamSource.linesFromGZip(gzipFile)) {
            List<String> result = stream.collect(Collectors.toList());
            assertEquals(LOG_LINES, result);
        }
    }

    @Test
    void linesAutoDetectsFormat() throws IOException {
        try (Stream<String> stream = LogStreamSource.lines(zipFile)) {
            List<String> result = stream.collect(Collectors.toList());
            assertEquals(LOG_LINES, result);
        }
    }

    @Test
    void linesWithExplicitFormat() throws IOException {
        try (Stream<String> stream = LogStreamSource.lines(gzipFile, LogSourceFormat.GZIP)) {
            List<String> result = stream.collect(Collectors.toList());
            assertEquals(LOG_LINES, result);
        }
    }

    @Test
    void sizeInBytes() throws IOException {
        long size = LogStreamSource.sizeInBytes(plainFile);
        assertTrue(size > 0);
        assertEquals(Files.size(plainFile), size);
    }

    @Test
    void linesFromZipSkipsDirectoryEntries() throws IOException {
        Path zipWithDir = tempDir.resolve("withdir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipWithDir.toFile()))) {
            zos.putNextEntry(new ZipEntry("subdir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("subdir/gc.log"));
            for (String line : LOG_LINES) {
                zos.write((line + "\n").getBytes());
            }
            zos.closeEntry();
        }

        try (Stream<String> stream = LogStreamSource.linesFromZip(zipWithDir)) {
            List<String> result = stream.collect(Collectors.toList());
            assertEquals(LOG_LINES, result);
        }
    }

    @Test
    void linesFromEmptyZipThrows() throws IOException {
        Path emptyZip = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(emptyZip.toFile()))) {
            // no entries
        }

        assertThrows(IOException.class, () -> LogStreamSource.linesFromZip(emptyZip));
    }

    @Test
    void linesWithDirectoryFormatThrows() {
        assertThrows(IOException.class, () -> LogStreamSource.lines(tempDir, LogSourceFormat.DIRECTORY));
    }
}

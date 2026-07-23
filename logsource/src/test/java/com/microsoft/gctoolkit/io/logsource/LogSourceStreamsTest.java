// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
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

class LogSourceStreamsTest {

    private static final String LINE_1 = "[0.011s][info][gc] Using G1";
    private static final String LINE_2 = "[0.012s][info][gc] Heap region size: 1M";
    private static final String LINE_3 = "[0.013s][info][gc] GC(0) Pause Young";

    @TempDir
    Path tempDir;

    @Test
    void detectFormatPlainText() throws IOException {
        Path file = createPlainTextFile("gc.log");
        assertEquals(LogSourceStreams.FileFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatGZip() throws IOException {
        Path file = createGZipFile("gc.log.gz");
        assertEquals(LogSourceStreams.FileFormat.GZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path file = createZipFile("gc.log.zip", "gc.log");
        assertEquals(LogSourceStreams.FileFormat.ZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(LogSourceStreams.FileFormat.DIRECTORY, LogSourceStreams.detectFormat(tempDir));
    }

    @Test
    void streamPlainTextFileReturnsAllLines() throws IOException {
        Path file = createPlainTextFile("gc.log");
        try (Stream<String> stream = LogSourceStreams.streamPlainTextFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2, LINE_3), lines);
        }
    }

    @Test
    void streamGZipFileReturnsAllLines() throws IOException {
        Path file = createGZipFile("gc.log.gz");
        try (Stream<String> stream = LogSourceStreams.streamGZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2, LINE_3), lines);
        }
    }

    @Test
    void streamZipFileReturnsAllLines() throws IOException {
        Path file = createZipFile("gc.log.zip", "gc.log");
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2, LINE_3), lines);
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries() throws IOException {
        Path file = createZipFileWithDirectory("gc-with-dir.zip");
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2, LINE_3), lines);
        }
    }

    @Test
    void streamMultiEntryZipFileReturnsAllEntries() throws IOException {
        Path file = createMultiEntryZipFile("multi.zip");
        try (Stream<String> stream = LogSourceStreams.streamMultiEntryZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void streamByFormatPlainText() throws IOException {
        Path file = createPlainTextFile("gc.log");
        try (Stream<String> stream = LogSourceStreams.streamByFormat(file, LogSourceStreams.FileFormat.PLAINTEXT)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
        }
    }

    @Test
    void streamByFormatDirectory() {
        assertThrows(IOException.class,
                () -> LogSourceStreams.streamByFormat(tempDir, LogSourceStreams.FileFormat.DIRECTORY));
    }

    @Test
    void streamByFormatUnknown() {
        assertThrows(IOException.class,
                () -> LogSourceStreams.streamByFormat(tempDir, LogSourceStreams.FileFormat.UNKNOWN));
    }

    @Test
    void matchesMagicReturnsFalseForNonexistentFile() {
        Path nonexistent = tempDir.resolve("does-not-exist.log");
        assertEquals(false, LogSourceStreams.matchesMagic(nonexistent, 0x1F, 0x8b));
    }

    // --- Helper methods ---

    private Path createPlainTextFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        Files.write(file, List.of(LINE_1, LINE_2, LINE_3));
        return file;
    }

    private Path createGZipFile(String name) throws IOException {
        Path file = tempDir.resolve(name);
        try (GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            String content = LINE_1 + "\n" + LINE_2 + "\n" + LINE_3 + "\n";
            gzip.write(content.getBytes());
        }
        return file;
    }

    private Path createZipFile(String zipName, String entryName) throws IOException {
        Path file = tempDir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            zos.putNextEntry(new ZipEntry(entryName));
            String content = LINE_1 + "\n" + LINE_2 + "\n" + LINE_3 + "\n";
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return file;
    }

    private Path createZipFileWithDirectory(String zipName) throws IOException {
        Path file = tempDir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            String content = LINE_1 + "\n" + LINE_2 + "\n" + LINE_3 + "\n";
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return file;
    }

    private Path createMultiEntryZipFile(String zipName) throws IOException {
        Path file = tempDir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
            zos.putNextEntry(new ZipEntry("gc.log.0"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("gc.log.1"));
            zos.write((LINE_2 + "\n").getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("gc.log.2"));
            zos.write((LINE_3 + "\n").getBytes());
            zos.closeEntry();
        }
        return file;
    }
}

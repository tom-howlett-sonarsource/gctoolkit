// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogStreamSourceTest {

    @TempDir
    Path tempDir;

    private Path plainFile;
    private Path gzipFile;
    private Path zipFile;
    private Path multiEntryZipFile;

    private static final String LINE_1 = "first line of log";
    private static final String LINE_2 = "second line of log";

    @BeforeEach
    void setUp() throws IOException {
        plainFile = tempDir.resolve("gc.log");
        Files.write(plainFile, List.of(LINE_1, LINE_2));

        gzipFile = tempDir.resolve("gc.log.gz");
        try (OutputStream fos = Files.newOutputStream(gzipFile);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            gzos.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes());
        }

        zipFile = tempDir.resolve("gc.log.zip");
        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes());
            zos.closeEntry();
        }

        multiEntryZipFile = tempDir.resolve("gc-multi.log.zip");
        try (OutputStream fos = Files.newOutputStream(multiEntryZipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("gc.log.0"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("gc.log.1"));
            zos.write((LINE_2 + "\n").getBytes());
            zos.closeEntry();
        }
    }

    @Test
    void detectFormatPlainText() {
        assertEquals(FileFormat.PLAINTEXT, LogStreamSource.detectFormat(plainFile));
    }

    @Test
    void detectFormatGzip() {
        assertEquals(FileFormat.GZIP, LogStreamSource.detectFormat(gzipFile));
    }

    @Test
    void detectFormatZip() {
        assertEquals(FileFormat.ZIP, LogStreamSource.detectFormat(zipFile));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(FileFormat.DIRECTORY, LogStreamSource.detectFormat(tempDir));
    }

    @Test
    void detectFormatNonExistentFile() {
        Path missing = tempDir.resolve("does-not-exist.log");
        assertEquals(FileFormat.PLAINTEXT, LogStreamSource.detectFormat(missing));
    }

    @Test
    void streamPlainTextFile() throws IOException {
        try (Stream<String> lines = LogStreamSource.stream(plainFile, FileFormat.PLAINTEXT)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), result);
        }
    }

    @Test
    void streamGzipFile() throws IOException {
        try (Stream<String> lines = LogStreamSource.stream(gzipFile, FileFormat.GZIP)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), result);
        }
    }

    @Test
    void streamZipFile() throws IOException {
        try (Stream<String> lines = LogStreamSource.stream(zipFile, FileFormat.ZIP)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), result);
        }
    }

    @Test
    void streamDirectoryThrows() {
        assertThrows(IOException.class,
                () -> LogStreamSource.stream(tempDir, FileFormat.DIRECTORY));
    }

    @Test
    void streamUnknownThrows() {
        assertThrows(IOException.class,
                () -> LogStreamSource.stream(plainFile, FileFormat.UNKNOWN));
    }

    @Test
    void streamMultiEntryZipFile() throws IOException {
        try (Stream<String> lines = LogStreamSource.streamMultiEntryZipFile(multiEntryZipFile)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), result);
        }
    }

    @Test
    void streamZipWithDirectoryEntry() throws IOException {
        Path zipWithDir = tempDir.resolve("gc-dir.zip");
        try (OutputStream fos = Files.newOutputStream(zipWithDir);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> lines = LogStreamSource.stream(zipWithDir, FileFormat.ZIP)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_1), result);
        }
    }

    @Test
    void byteSizeOfRegularFile() throws IOException {
        long size = LogStreamSource.byteSize(plainFile);
        assertTrue(size > 0);
        assertEquals(Files.size(plainFile), size);
    }

    @Test
    void byteSizeOfDirectory() throws IOException {
        long size = LogStreamSource.byteSize(tempDir);
        assertTrue(size > 0);
    }

    @Test
    void streamMultiEntryZipSkipsDirectories() throws IOException {
        Path zipWithDir = tempDir.resolve("gc-mixed.zip");
        try (OutputStream fos = Files.newOutputStream(zipWithDir);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("dir/gc.log.0"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("dir/gc.log.1"));
            zos.write((LINE_2 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> lines = LogStreamSource.streamMultiEntryZipFile(zipWithDir)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), result);
        }
    }
}

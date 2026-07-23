// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourcesTest {

    private static final String GC_LOG_ENTRY = "gc.log";

    @TempDir
    Path tempDir;

    @Test
    void detectFormatPlainText() throws IOException {
        Path file = tempDir.resolve(GC_LOG_ENTRY);
        Files.write(file, List.of("line1", "line2"));
        assertEquals(LogSources.FileFormat.PLAINTEXT, LogSources.detectFormat(file));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(LogSources.FileFormat.DIRECTORY, LogSources.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path file = createZipFile("gc.log.zip", GC_LOG_ENTRY, "alpha\nbeta\n");
        assertEquals(LogSources.FileFormat.ZIP, LogSources.detectFormat(file));
    }

    @Test
    void detectFormatGZip() throws IOException {
        Path file = createGZipFile("gc.log.gz", "gamma\ndelta\n");
        assertEquals(LogSources.FileFormat.GZIP, LogSources.detectFormat(file));
    }

    @Test
    void detectFormatUnknownForMissingFile() {
        Path missing = tempDir.resolve("no-such-file");
        assertEquals(LogSources.FileFormat.UNKNOWN, LogSources.detectFormat(missing));
    }

    @Test
    void streamPlainText() throws IOException {
        Path file = tempDir.resolve("plain.log");
        Files.write(file, List.of("one", "two", "three"));
        try (Stream<String> stream = LogSources.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("one", "two", "three"), lines);
        }
    }

    @Test
    void streamZip() throws IOException {
        Path file = createZipFile("test.zip", "inner.log", "zipline1\nzipline2\n");
        try (Stream<String> stream = LogSources.streamZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("zipline1", "zipline2"), lines);
        }
    }

    @Test
    void streamZipSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("dirs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write("dir_line1\ndir_line2\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSources.streamZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("dir_line1", "dir_line2"), lines);
        }
    }

    @Test
    void streamGZip() throws IOException {
        Path file = createGZipFile("test.gz", "gzline1\ngzline2\n");
        try (Stream<String> stream = LogSources.streamGZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("gzline1", "gzline2"), lines);
        }
    }

    @Test
    void streamAutoDetectsPlainText() throws IOException {
        Path file = tempDir.resolve("auto.log");
        Files.write(file, List.of("auto1", "auto2"));
        try (Stream<String> stream = LogSources.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("auto1", "auto2"), lines);
        }
    }

    @Test
    void streamAutoDetectsZip() throws IOException {
        Path file = createZipFile("auto.zip", GC_LOG_ENTRY, "zauto1\nzauto2\n");
        try (Stream<String> stream = LogSources.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("zauto1", "zauto2"), lines);
        }
    }

    @Test
    void streamAutoDetectsGZip() throws IOException {
        Path file = createGZipFile("auto.gz", "gauto1\ngauto2\n");
        try (Stream<String> stream = LogSources.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("gauto1", "gauto2"), lines);
        }
    }

    @Test
    void streamThrowsForDirectory() {
        assertThrows(IOException.class, () -> LogSources.stream(tempDir));
    }

    @Test
    void byteSize() throws IOException {
        Path file = tempDir.resolve("sized.log");
        byte[] content = "hello world\n".getBytes();
        Files.write(file, content);
        assertEquals(content.length, LogSources.byteSize(file));
    }

    @Test
    void byteSizeThrowsForMissingFile() {
        Path missing = tempDir.resolve("missing.log");
        assertThrows(IOException.class, () -> LogSources.byteSize(missing));
    }

    private Path createZipFile(String name, String entryName, String content) throws IOException {
        Path file = tempDir.resolve(name);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return file;
    }

    private Path createGZipFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        try (OutputStream os = Files.newOutputStream(file);
             GZIPOutputStream gzos = new GZIPOutputStream(os)) {
            gzos.write(content.getBytes());
        }
        return file;
    }
}

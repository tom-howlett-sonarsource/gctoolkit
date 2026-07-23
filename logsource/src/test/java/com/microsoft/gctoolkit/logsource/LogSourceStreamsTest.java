// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceStreamsTest {

    @TempDir
    Path tempDir;

    @Test
    void detectPlainTextFormat() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.write(file, List.of("[0.001s] Using G1"));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectDirectoryFormat() {
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceStreams.detectFormat(tempDir));
    }

    @Test
    void detectZipFormat() throws IOException {
        Path file = createZipFile("gc.log", "line1\nline2\n");
        assertEquals(LogSourceFormat.ZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectGZipFormat() throws IOException {
        Path file = createGZipFile("line1\nline2\n");
        assertEquals(LogSourceFormat.GZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void streamPlainFile() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.write(file, List.of("line1", "line2", "line3"));
        try (Stream<String> stream = LogSourceStreams.streamPlain(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("line1", "line2", "line3"), lines);
        }
    }

    @Test
    void streamZipFile() throws IOException {
        Path file = createZipFile("gc.log", "zip-line1\nzip-line2\n");
        try (Stream<String> stream = LogSourceStreams.streamZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("zip-line1", "zip-line2"), lines);
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("dirs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write("content\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceStreams.streamZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("content"), lines);
        }
    }

    @Test
    void streamGZipFile() throws IOException {
        Path file = createGZipFile("gzip-line1\ngzip-line2\n");
        try (Stream<String> stream = LogSourceStreams.streamGZip(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("gzip-line1", "gzip-line2"), lines);
        }
    }

    @Test
    void streamAutoDetectsPlain() throws IOException {
        Path file = tempDir.resolve("auto.log");
        Files.write(file, List.of("auto-plain"));
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals("auto-plain", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void streamAutoDetectsZip() throws IOException {
        Path file = createZipFile("gc.log", "auto-zip\n");
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals("auto-zip", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void streamAutoDetectsGZip() throws IOException {
        Path file = createGZipFile("auto-gzip\n");
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals("auto-gzip", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void streamDirectoryThrows() {
        assertThrows(IOException.class, () -> LogSourceStreams.stream(tempDir));
    }

    @Test
    void streamWithExplicitFormatMatchesAutoDetect() throws IOException {
        Path file = createGZipFile("explicit\n");
        try (Stream<String> stream = LogSourceStreams.stream(file, LogSourceFormat.GZIP)) {
            assertEquals("explicit", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void fileSizeReturnsCorrectSize() throws IOException {
        Path file = tempDir.resolve("size.log");
        byte[] content = "hello world\n".getBytes();
        Files.write(file, content);
        assertEquals(content.length, LogSourceStreams.fileSize(file));
    }

    @Test
    void fileSizeOfEmptyFile() throws IOException {
        Path file = tempDir.resolve("empty.log");
        Files.createFile(file);
        assertEquals(0L, LogSourceStreams.fileSize(file));
    }

    @Test
    void detectFormatOfEmptyFileIsPlainText() throws IOException {
        Path file = tempDir.resolve("empty.log");
        Files.createFile(file);
        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void streamUnknownFormatThrows() {
        Path file = tempDir.resolve("unknown.log");
        assertThrows(IOException.class,
                () -> LogSourceStreams.stream(file, LogSourceFormat.UNKNOWN));
    }

    private Path createZipFile(String entryName, String content) throws IOException {
        Path file = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return file;
    }

    private Path createGZipFile(String content) throws IOException {
        Path file = tempDir.resolve("test.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(file))) {
            gos.write(content.getBytes());
        }
        return file;
    }
}

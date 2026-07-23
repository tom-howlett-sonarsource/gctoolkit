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

class LogSourceStreamsTest {

    @TempDir
    Path tempDir;

    // --- detect() ---

    @Test
    void detectPlainTextFile() throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, "Hello\nWorld\n");
        assertEquals(FileFormat.PLAINTEXT, LogSourceStreams.detect(file));
    }

    @Test
    void detectDirectory() {
        assertEquals(FileFormat.DIRECTORY, LogSourceStreams.detect(tempDir));
    }

    @Test
    void detectGzipFile() throws IOException {
        Path file = tempDir.resolve("test.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(file))) {
            gos.write("Hello\nWorld\n".getBytes());
        }
        assertEquals(FileFormat.GZIP, LogSourceStreams.detect(file));
    }

    @Test
    void detectZipFile() throws IOException {
        Path file = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("test.log"));
            zos.write("Hello\nWorld\n".getBytes());
            zos.closeEntry();
        }
        assertEquals(FileFormat.ZIP, LogSourceStreams.detect(file));
    }

    @Test
    void detectNonExistentFileReturnPlainText() {
        // Non-existent file cannot be read, magic bytes fail -> defaults to plain text
        Path missing = tempDir.resolve("nonexistent.log");
        assertEquals(FileFormat.PLAINTEXT, LogSourceStreams.detect(missing));
    }

    // --- streamPlainText() ---

    @Test
    void streamPlainTextFile() throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, "line1\nline2\nline3\n");
        try (Stream<String> stream = LogSourceStreams.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("line1", "line2", "line3"), lines);
        }
    }

    // --- streamGZipFile() ---

    @Test
    void streamGzipFile() throws IOException {
        Path file = tempDir.resolve("test.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(file))) {
            gos.write("line1\nline2\n".getBytes());
        }
        try (Stream<String> stream = LogSourceStreams.streamGZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("line1", "line2"), lines);
        }
    }

    // --- streamZipFile() ---

    @Test
    void streamZipFile() throws IOException {
        Path file = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("test.log"));
            zos.write("line1\nline2\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("line1", "line2"), lines);
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("dir/test.log"));
            zos.write("content\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("content"), lines);
        }
    }

    // --- stream(Path) auto-detection ---

    @Test
    void streamAutoDetectsPlainText() throws IOException {
        Path file = tempDir.resolve("auto.log");
        Files.writeString(file, "plain\n");
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals(List.of("plain"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamAutoDetectsGzip() throws IOException {
        Path file = tempDir.resolve("auto.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(file))) {
            gos.write("gzip\n".getBytes());
        }
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals(List.of("gzip"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamAutoDetectsZip() throws IOException {
        Path file = tempDir.resolve("auto.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("log.txt"));
            zos.write("zip\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals(List.of("zip"), stream.collect(Collectors.toList()));
        }
    }

    // --- stream(Path, FileFormat) ---

    @Test
    void streamWithExplicitFormat() throws IOException {
        Path file = tempDir.resolve("explicit.log");
        Files.writeString(file, "explicit\n");
        try (Stream<String> stream = LogSourceStreams.stream(file, FileFormat.PLAINTEXT)) {
            assertEquals(List.of("explicit"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamDirectoryThrowsIOException() {
        assertThrows(IOException.class, () -> LogSourceStreams.stream(tempDir));
    }

    @Test
    void streamUnknownFormatThrowsIOException() {
        Path file = tempDir.resolve("unknown.log");
        assertThrows(IOException.class, () -> LogSourceStreams.stream(file, FileFormat.UNKNOWN));
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

    private Path createPlainTextFile(String... lines) throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.write(file, Arrays.asList(lines));
        return file;
    }

    private Path createGzipFile(String... lines) throws IOException {
        Path file = tempDir.resolve("test.log.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(file));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(gzos))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
        return file;
    }

    private Path createZipFile(String... lines) throws IOException {
        Path file = tempDir.resolve("test.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("test.log"));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zos));
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
            writer.flush();
            zos.closeEntry();
        }
        return file;
    }

    @Test
    void detectFormatPlainText() throws IOException {
        Path file = createPlainTextFile("line1", "line2");
        assertEquals(LogSourceStreams.FileFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatGzip() throws IOException {
        Path file = createGzipFile("line1", "line2");
        assertEquals(LogSourceStreams.FileFormat.GZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path file = createZipFile("line1", "line2");
        assertEquals(LogSourceStreams.FileFormat.ZIP, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(LogSourceStreams.FileFormat.DIRECTORY, LogSourceStreams.detectFormat(tempDir));
    }

    @Test
    void detectFormatNonexistentFile() {
        Path missing = tempDir.resolve("nonexistent.log");
        assertEquals(LogSourceStreams.FileFormat.UNKNOWN, LogSourceStreams.detectFormat(missing));
    }

    @Test
    void streamPlainTextReadsAllLines() throws IOException {
        Path file = createPlainTextFile("line1", "line2", "line3");
        try (Stream<String> stream = LogSourceStreams.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(Arrays.asList("line1", "line2", "line3"), lines);
        }
    }

    @Test
    void streamZipFileReadsAllLines() throws IOException {
        Path file = createZipFile("line1", "line2", "line3");
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(Arrays.asList("line1", "line2", "line3"), lines);
        }
    }

    @Test
    void streamGZipFileReadsAllLines() throws IOException {
        Path file = createGzipFile("line1", "line2", "line3");
        try (Stream<String> stream = LogSourceStreams.streamGZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(Arrays.asList("line1", "line2", "line3"), lines);
        }
    }

    @Test
    void streamDispatchesPlainText() throws IOException {
        Path file = createPlainTextFile("hello");
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals("hello", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void streamDispatchesZip() throws IOException {
        Path file = createZipFile("zip-content");
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals("zip-content", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void streamDispatchesGzip() throws IOException {
        Path file = createGzipFile("gzip-content");
        try (Stream<String> stream = LogSourceStreams.stream(file)) {
            assertEquals("gzip-content", stream.findFirst().orElseThrow());
        }
    }

    @Test
    void streamDirectoryThrows() {
        assertThrows(IOException.class, () -> LogSourceStreams.stream(tempDir));
    }

    @Test
    void sizeReturnsFileSize() throws IOException {
        Path file = createPlainTextFile("hello");
        long size = LogSourceStreams.size(file);
        assertTrue(size > 0);
        assertEquals(Files.size(file), size);
    }

    @Test
    void detectFormatPartialGzipMagicFallsBackToPlaintext() throws IOException {
        Path file = tempDir.resolve("partial-gzip.log");
        Files.write(file, new byte[]{0x1F, 0x00});
        assertEquals(LogSourceStreams.FileFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void detectFormatPartialZipMagicFallsBackToPlaintext() throws IOException {
        Path file = tempDir.resolve("partial-zip.log");
        Files.write(file, new byte[]{0x50, 0x00});
        assertEquals(LogSourceStreams.FileFormat.PLAINTEXT, LogSourceStreams.detectFormat(file));
    }

    @Test
    void streamZipFileWithNoEntriesReturnsEmptyStream() throws IOException {
        Path file = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            // no entries added
        }
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            assertEquals(0, stream.count());
        }
    }

    @Test
    void zipFileSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("withdir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("subdir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("subdir/test.log"));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zos));
            writer.write("content");
            writer.newLine();
            writer.flush();
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceStreams.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(Arrays.asList("content"), lines);
        }
    }
}

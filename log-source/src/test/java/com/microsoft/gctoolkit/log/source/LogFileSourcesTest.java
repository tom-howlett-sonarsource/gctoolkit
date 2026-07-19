// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.log.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileSourcesTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");

    @Test
    void detectsPlainText(@TempDir Path tempDir) throws IOException {
        Path file = writePlain(tempDir.resolve("gc.log"));
        assertEquals(LogFileFormat.PLAIN_TEXT, LogFileSources.detectFormat(file));
    }

    @Test
    void detectsDirectory(@TempDir Path tempDir) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileSources.detectFormat(tempDir));
    }

    @Test
    void detectsZip(@TempDir Path tempDir) throws IOException {
        Path zip = writeZip(tempDir.resolve("gc.zip"));
        assertEquals(LogFileFormat.ZIP, LogFileSources.detectFormat(zip));
    }

    @Test
    void detectsGZip(@TempDir Path tempDir) throws IOException {
        Path gz = writeGZip(tempDir.resolve("gc.log.gz"));
        assertEquals(LogFileFormat.GZIP, LogFileSources.detectFormat(gz));
    }

    @Test
    void detectFormatHandlesMissingFile(@TempDir Path tempDir) {
        assertEquals(LogFileFormat.UNKNOWN, LogFileSources.detectFormat(tempDir.resolve("missing.log")));
    }

    @Test
    void detectFormatHandlesNullPath() {
        assertEquals(LogFileFormat.UNKNOWN, LogFileSources.detectFormat(null));
    }

    @Test
    void matchesMagicReturnsFalseForShortFile(@TempDir Path tempDir) throws IOException {
        Path shortFile = tempDir.resolve("short");
        Files.write(shortFile, new byte[]{0x50});
        assertFalse(LogFileSources.matchesMagic(shortFile, 0x50, 0x4B));
    }

    @Test
    void byteSizeReturnsFileSize(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("bytes.log");
        Files.write(file, "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(5, LogFileSources.byteSize(file));
    }

    @Test
    void openLinesReadsPlainText(@TempDir Path tempDir) throws IOException {
        Path file = writePlain(tempDir.resolve("gc.log"));
        assertEquals(LINES, collect(LogFileSources.openLines(file)));
    }

    @Test
    void openLinesReadsZip(@TempDir Path tempDir) throws IOException {
        Path zip = writeZip(tempDir.resolve("gc.zip"));
        assertEquals(LINES, collect(LogFileSources.openLines(zip)));
    }

    @Test
    void openLinesReadsGZip(@TempDir Path tempDir) throws IOException {
        Path gz = writeGZip(tempDir.resolve("gc.log.gz"));
        assertEquals(LINES, collect(LogFileSources.openLines(gz)));
    }

    @Test
    void openLinesWithExplicitFormat(@TempDir Path tempDir) throws IOException {
        Path file = writePlain(tempDir.resolve("gc.log"));
        assertEquals(LINES, collect(LogFileSources.openLines(file, LogFileFormat.PLAIN_TEXT)));
    }

    @Test
    void openLinesThrowsWhenFormatUnknown(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.log");
        assertThrows(IOException.class, () -> LogFileSources.openLines(missing));
    }

    @Test
    void openLinesThrowsWhenFormatIsDirectory(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> LogFileSources.openLines(tempDir, LogFileFormat.DIRECTORY));
    }

    @Test
    void openZipStreamSkipsDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("with-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
            ZipEntry entry = new ZipEntry("gc.log");
            out.putNextEntry(entry);
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(LINES, collect(LogFileSources.openZipStream(zip)));
    }

    @Test
    void openZipStreamHandlesZipWithOnlyDirectories(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("only-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
        }
        // No non-directory entries; opening should still succeed and return an empty stream.
        assertTrue(collect(LogFileSources.openZipStream(zip)).isEmpty());
    }

    @Test
    void detectFormatFallsBackToPlainOnBinaryHeader(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain-binary");
        Files.write(file, new byte[]{0x00, 0x01, 0x02, 0x03});
        assertEquals(LogFileFormat.PLAIN_TEXT, LogFileSources.detectFormat(file));
    }

    private static Path writePlain(Path path) throws IOException {
        Files.write(path, LINES);
        return path;
    }

    private static Path writeZip(Path path) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            ZipEntry entry = new ZipEntry("gc.log");
            out.putNextEntry(entry);
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return path;
    }

    private static Path writeGZip(Path path) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private static List<String> collect(Stream<String> stream) {
        try (Stream<String> s = stream) {
            return s.collect(Collectors.toList());
        }
    }
}

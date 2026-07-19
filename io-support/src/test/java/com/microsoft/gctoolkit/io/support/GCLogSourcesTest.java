// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final String LINE_ONE = "gc: line one";
    private static final String LINE_TWO = "gc: line two";

    @Test
    void detectsPlainTextFile(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.log");
        Files.write(plain, List.of(LINE_ONE, LINE_TWO));
        assertEquals(SourceFormat.PLAINTEXT, GCLogSources.detect(plain));
    }

    @Test
    void detectsGZipFile(@TempDir Path tempDir) throws IOException {
        Path gz = writeGzip(tempDir.resolve("log.gz"));
        assertEquals(SourceFormat.GZIP, GCLogSources.detect(gz));
    }

    @Test
    void detectsZipFile(@TempDir Path tempDir) throws IOException {
        Path zip = writeZip(tempDir.resolve("log.zip"), "entry.log");
        assertEquals(SourceFormat.ZIP, GCLogSources.detect(zip));
    }

    @Test
    void detectsDirectory(@TempDir Path tempDir) {
        assertEquals(SourceFormat.DIRECTORY, GCLogSources.detect(tempDir));
    }

    @Test
    void detectReturnsUnknownForMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nope.log");
        assertEquals(SourceFormat.UNKNOWN, GCLogSources.detect(missing));
    }

    @Test
    void detectReturnsUnknownForNullPath() {
        assertEquals(SourceFormat.UNKNOWN, GCLogSources.detect(null));
    }

    @Test
    void byteSizeReportsFileLength(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("sized.log");
        byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        Files.write(plain, bytes);
        assertEquals(bytes.length, GCLogSources.byteSize(plain));
    }

    @Test
    void byteSizeReturnsNegativeOneForDirectory(@TempDir Path tempDir) {
        assertEquals(-1L, GCLogSources.byteSize(tempDir));
    }

    @Test
    void byteSizeReturnsNegativeOneForMissingFile(@TempDir Path tempDir) {
        assertEquals(-1L, GCLogSources.byteSize(tempDir.resolve("missing.log")));
    }

    @Test
    void byteSizeReturnsNegativeOneForNullPath() {
        assertEquals(-1L, GCLogSources.byteSize(null));
    }

    @Test
    void openPlainStreamsAllLines(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.log");
        Files.write(plain, List.of(LINE_ONE, LINE_TWO));
        try (Stream<String> lines = GCLogSources.openPlain(plain)) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_ONE, LINE_TWO), collected);
        }
    }

    @Test
    void openGZipStreamsAllLines(@TempDir Path tempDir) throws IOException {
        Path gz = writeGzip(tempDir.resolve("log.gz"));
        try (Stream<String> lines = GCLogSources.openGZip(gz)) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_ONE, LINE_TWO), collected);
        }
    }

    @Test
    void openZipStreamsFirstEntryLines(@TempDir Path tempDir) throws IOException {
        Path zip = writeZip(tempDir.resolve("log.zip"), "entry.log");
        try (Stream<String> lines = GCLogSources.openZip(zip)) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_ONE, LINE_TWO), collected);
        }
    }

    @Test
    void openZipSkipsLeadingDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("mixed.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write((LINE_ONE + "\n" + LINE_TWO + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> lines = GCLogSources.openZip(zip)) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(LINE_ONE, LINE_TWO), collected);
        }
    }

    @Test
    void openZipThrowsWhenEmpty(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            // no entries
        }
        assertThrows(IOException.class, () -> GCLogSources.openZip(zip));
    }

    @Test
    void openLinesDispatchesOnFormat(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("dispatch.log");
        Files.write(plain, List.of(LINE_ONE));
        try (Stream<String> lines = GCLogSources.openLines(plain)) {
            assertEquals(List.of(LINE_ONE), lines.collect(Collectors.toList()));
        }

        Path gz = writeGzip(tempDir.resolve("dispatch.gz"));
        try (Stream<String> lines = GCLogSources.openLines(gz)) {
            assertEquals(List.of(LINE_ONE, LINE_TWO), lines.collect(Collectors.toList()));
        }

        Path zip = writeZip(tempDir.resolve("dispatch.zip"), "inner.log");
        try (Stream<String> lines = GCLogSources.openLines(zip)) {
            assertEquals(List.of(LINE_ONE, LINE_TWO), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openLinesRejectsDirectories(@TempDir Path tempDir) {
        IOException failure = assertThrows(IOException.class, () -> GCLogSources.openLines(tempDir));
        assertNotNull(failure.getMessage());
        assertTrue(failure.getMessage().contains("DIRECTORY"));
    }

    @Test
    void openLinesRejectsMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("gone.log");
        IOException failure = assertThrows(IOException.class, () -> GCLogSources.openLines(missing));
        assertTrue(failure.getMessage().contains("UNKNOWN"));
    }

    @Test
    void enumValuesCoverAllKnownFormats() {
        assertEquals(5, SourceFormat.values().length);
        assertNotNull(SourceFormat.valueOf("PLAINTEXT"));
        assertFalse(SourceFormat.PLAINTEXT == SourceFormat.UNKNOWN);
    }

    private static Path writeGzip(Path path) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write((LINE_ONE + "\n" + LINE_TWO + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private static Path writeZip(Path path, String entryName) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write((LINE_ONE + "\n" + LINE_TWO + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return path;
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final String PLAIN_LOG = "plain.log";

    @Test
    void detectsPlainTextFormat(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(PLAIN_LOG);
        Files.write(file, List.of("hello", "world"));
        assertEquals(GCLogSourceFormat.PLAINTEXT, GCLogSources.detectFormat(file));
    }

    @Test
    void detectsDirectoryFormat(@TempDir Path dir) {
        assertEquals(GCLogSourceFormat.DIRECTORY, GCLogSources.detectFormat(dir));
    }

    @Test
    void detectsZipFormat(@TempDir Path dir) throws IOException {
        Path file = writeZip(dir.resolve("logs.zip"), "one.log", "line-a\nline-b\n");
        assertEquals(GCLogSourceFormat.ZIP, GCLogSources.detectFormat(file));
    }

    @Test
    void detectsGZipFormat(@TempDir Path dir) throws IOException {
        Path file = writeGZip(dir.resolve("log.gz"), "gzip-a\ngzip-b\n");
        assertEquals(GCLogSourceFormat.GZIP, GCLogSources.detectFormat(file));
    }

    @Test
    void openPlainStreamReadsLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(PLAIN_LOG);
        Files.write(file, List.of("a", "b", "c"));
        try (Stream<String> stream = GCLogSources.openLineStream(file)) {
            assertEquals(List.of("a", "b", "c"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipStreamReadsFirstEntry(@TempDir Path dir) throws IOException {
        Path file = writeZip(dir.resolve("logs.zip"), "entry.log", "z1\nz2\n");
        try (Stream<String> stream = GCLogSources.openLineStream(file)) {
            assertEquals(List.of("z1", "z2"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipStreamReadsAllLines(@TempDir Path dir) throws IOException {
        Path file = writeGZip(dir.resolve("log.gz"), "g1\ng2\ng3\n");
        try (Stream<String> stream = GCLogSources.openLineStream(file)) {
            assertEquals(List.of("g1", "g2", "g3"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openLineStreamRejectsUnknownFormat(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(PLAIN_LOG);
        Files.write(file, List.of("x"));
        assertThrows(IOException.class,
                () -> GCLogSources.openLineStream(file, GCLogSourceFormat.UNKNOWN));
    }

    @Test
    void openLineStreamRejectsDirectory(@TempDir Path dir) {
        assertThrows(IOException.class,
                () -> GCLogSources.openLineStream(dir, GCLogSourceFormat.DIRECTORY));
    }

    @Test
    void sizeReportsFileLength(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(PLAIN_LOG);
        byte[] payload = "abcdef".getBytes(StandardCharsets.UTF_8);
        Files.write(file, payload);
        assertEquals(payload.length, GCLogSources.size(file));
    }

    @Test
    void detectFormatReturnsPlaintextOnUnreadable(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("empty.log");
        Files.createFile(file);
        assertTrue(Files.size(file) == 0);
        assertEquals(GCLogSourceFormat.PLAINTEXT, GCLogSources.detectFormat(file));
    }

    private static Path writeZip(Path target, String entryName, String content) throws IOException {
        try (OutputStream fout = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(fout)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return target;
    }

    private static Path writeGZip(Path target, String content) throws IOException {
        try (OutputStream fout = Files.newOutputStream(target);
             GZIPOutputStream gz = new GZIPOutputStream(fout)) {
            gz.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return target;
    }
}

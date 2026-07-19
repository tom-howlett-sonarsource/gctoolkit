// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

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

class GCLogSourcesTest {

    private static final String LINE_ONE = "0.100: [GC pause (young) 128M->64M(256M)]";
    private static final String LINE_TWO = "0.200: [GC pause (young) 130M->65M(256M)]";

    @Test
    void detectPlaintext(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log");
        Files.writeString(file, LINE_ONE + "\n" + LINE_TWO + "\n");

        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.detectFormat(file));
    }

    @Test
    void detectDirectory(@TempDir Path tmp) {
        assertEquals(LogFileFormat.DIRECTORY, GCLogSources.detectFormat(tmp));
    }

    @Test
    void detectGzip(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write((LINE_ONE + "\n").getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(LogFileFormat.GZIP, GCLogSources.detectFormat(file));
    }

    @Test
    void detectZip(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log.zip");
        writeZip(file, "gc.log", LINE_ONE + "\n" + LINE_TWO + "\n");

        assertEquals(LogFileFormat.ZIP, GCLogSources.detectFormat(file));
    }

    @Test
    void detectFormatOfNullIsUnknown() {
        assertEquals(LogFileFormat.UNKNOWN, GCLogSources.detectFormat(null));
    }

    @Test
    void magicBytesMatchesOnlyRealMagic(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log");
        Files.writeString(file, "hello");

        assertFalse(GCLogSources.matchesMagic(file, GCLogSources.GZIP_MAGIC1, GCLogSources.GZIP_MAGIC2));
        assertFalse(GCLogSources.matchesMagic(tmp.resolve("does-not-exist"),
                GCLogSources.ZIP_MAGIC1, GCLogSources.ZIP_MAGIC2));
    }

    @Test
    void byteSizeReturnsFileSize(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log");
        byte[] payload = (LINE_ONE + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(file, payload);

        assertEquals(payload.length, GCLogSources.byteSize(file));
    }

    @Test
    void openPlainLinesStreamsAllRows(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log");
        Files.writeString(file, LINE_ONE + "\n" + LINE_TWO + "\n");

        try (Stream<String> lines = GCLogSources.openPlainLines(file)) {
            assertEquals(List.of(LINE_ONE, LINE_TWO), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openGzipLinesStreamsAllRows(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write((LINE_ONE + "\n" + LINE_TWO + "\n").getBytes(StandardCharsets.UTF_8));
        }

        try (Stream<String> lines = GCLogSources.openGzipLines(file)) {
            assertEquals(List.of(LINE_ONE, LINE_TWO), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipLinesSkipsLeadingDirectoryEntry(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write((LINE_ONE + "\n" + LINE_TWO + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        try (Stream<String> lines = GCLogSources.openZipLines(file)) {
            assertEquals(List.of(LINE_ONE, LINE_TWO), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openLinesDispatchesByFormat(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("plain.log");
        Files.writeString(plain, LINE_ONE + "\n");
        try (Stream<String> lines = GCLogSources.openLines(plain)) {
            assertEquals(List.of(LINE_ONE), lines.collect(Collectors.toList()));
        }

        Path gzip = tmp.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            out.write((LINE_ONE + "\n").getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<String> lines = GCLogSources.openLines(gzip)) {
            assertEquals(List.of(LINE_ONE), lines.collect(Collectors.toList()));
        }

        Path zip = tmp.resolve("plain.log.zip");
        writeZip(zip, "gc.log", LINE_ONE + "\n");
        try (Stream<String> lines = GCLogSources.openLines(zip)) {
            assertEquals(List.of(LINE_ONE), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openLinesRejectsDirectories(@TempDir Path tmp) {
        assertThrows(IOException.class, () -> GCLogSources.openLines(tmp));
    }

    @Test
    void utilityClassIsNotInstantiable() {
        assertTrue(java.lang.reflect.Modifier.isFinal(GCLogSources.class.getModifiers()));
    }

    private static void writeZip(Path zip, String entryName, String payload) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(payload.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}

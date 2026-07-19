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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> SAMPLE_LINES = List.of("first", "second", "third");
    private static final String PLAIN_NAME = "plain.log";
    private static final String ZIP_NAME = "logs.zip";
    private static final String GZ_NAME = "logs.log.gz";
    private static final String ENTRY_NAME = "gc.log";

    @Test
    void detectPlainText(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.writeString(plain, "hello\nworld\n");
        assertEquals(SourceFormat.PLAINTEXT, GCLogSources.detectFormat(plain));
    }

    @Test
    void detectZip(@TempDir Path tmp) throws IOException {
        Path zip = writeZip(tmp.resolve(ZIP_NAME), ENTRY_NAME, SAMPLE_LINES);
        assertEquals(SourceFormat.ZIP, GCLogSources.detectFormat(zip));
    }

    @Test
    void detectGZip(@TempDir Path tmp) throws IOException {
        Path gz = writeGZip(tmp.resolve(GZ_NAME), SAMPLE_LINES);
        assertEquals(SourceFormat.GZIP, GCLogSources.detectFormat(gz));
    }

    @Test
    void detectDirectory(@TempDir Path tmp) {
        assertEquals(SourceFormat.DIRECTORY, GCLogSources.detectFormat(tmp));
    }

    @Test
    void detectReturnsPlaintextWhenMagicUnreadable(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.log");
        assertEquals(SourceFormat.PLAINTEXT, GCLogSources.detectFormat(missing));
    }

    @Test
    void byteSizeMatchesFileLength(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        byte[] payload = "some content".getBytes(StandardCharsets.UTF_8);
        Files.write(plain, payload);
        assertEquals(payload.length, GCLogSources.byteSize(plain));
    }

    @Test
    void openPlainTextStreamsLines(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.write(plain, SAMPLE_LINES);
        try (Stream<String> stream = GCLogSources.openPlainText(plain)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipStreamsFirstEntry(@TempDir Path tmp) throws IOException {
        Path zip = writeZip(tmp.resolve(ZIP_NAME), ENTRY_NAME, SAMPLE_LINES);
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsLeadingDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve(ZIP_NAME);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write(String.join("\n", SAMPLE_LINES).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipStreamsLines(@TempDir Path tmp) throws IOException {
        Path gz = writeGZip(tmp.resolve(GZ_NAME), SAMPLE_LINES);
        try (Stream<String> stream = GCLogSources.openGZip(gz)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openLinesDispatchesByFormat(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.write(plain, SAMPLE_LINES);
        try (Stream<String> stream = GCLogSources.openLines(plain, SourceFormat.PLAINTEXT)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }

        Path zip = writeZip(tmp.resolve(ZIP_NAME), ENTRY_NAME, SAMPLE_LINES);
        try (Stream<String> stream = GCLogSources.openLines(zip, SourceFormat.ZIP)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }

        Path gz = writeGZip(tmp.resolve(GZ_NAME), SAMPLE_LINES);
        try (Stream<String> stream = GCLogSources.openLines(gz, SourceFormat.GZIP)) {
            assertEquals(SAMPLE_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openLinesRejectsNonStreamableFormats(@TempDir Path tmp) {
        IOException dir = assertThrows(IOException.class,
                () -> GCLogSources.openLines(tmp, SourceFormat.DIRECTORY));
        assertTrue(dir.getMessage().contains(tmp.toString()));
        assertThrows(IOException.class,
                () -> GCLogSources.openLines(tmp.resolve("x.log"), SourceFormat.UNKNOWN));
    }

    private static Path writeZip(Path zip, String entryName, List<String> lines) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    private static Path writeGZip(Path gz, List<String> lines) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        }
        return gz;
    }
}

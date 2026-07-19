// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {

    private static final List<String> LINES = List.of("first", "second", "third");
    private static final String LINE_CONTENT = String.join("\n", LINES) + "\n";

    @Test
    void detectFormatIdentifiesPlaintext(@TempDir Path dir) throws IOException {
        Path file = writePlaintext(dir);
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.detectFormat(file));
    }

    @Test
    void detectFormatIdentifiesGzip(@TempDir Path dir) throws IOException {
        Path file = writeGzip(dir);
        assertEquals(LogSourceFormat.GZIP, LogSources.detectFormat(file));
    }

    @Test
    void detectFormatIdentifiesZip(@TempDir Path dir) throws IOException {
        Path file = writeZip(dir);
        assertEquals(LogSourceFormat.ZIP, LogSources.detectFormat(file));
    }

    @Test
    void detectFormatIdentifiesDirectory(@TempDir Path dir) {
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.detectFormat(dir));
    }

    @Test
    void detectFormatReturnsUnknownForMissingPath(@TempDir Path dir) {
        assertEquals(LogSourceFormat.UNKNOWN, LogSources.detectFormat(dir.resolve("nope.log")));
    }

    @Test
    void hasMagicReadsTwoByteHeader(@TempDir Path dir) throws IOException {
        Path gzip = writeGzip(dir);
        assertTrue(LogSources.hasMagic(gzip, LogSources.GZIP_MAGIC1, LogSources.GZIP_MAGIC2));
        assertFalse(LogSources.hasMagic(gzip, LogSources.ZIP_MAGIC1, LogSources.ZIP_MAGIC2));
    }

    @Test
    void byteSizeReturnsFileLength(@TempDir Path dir) throws IOException {
        Path file = writePlaintext(dir);
        assertEquals(Files.size(file), LogSources.byteSize(file));
    }

    @Test
    void byteSizeReturnsMinusOneForMissingPath(@TempDir Path dir) {
        assertEquals(-1L, LogSources.byteSize(dir.resolve("missing")));
    }

    @Test
    void openPlainLinesStreamsEveryLine(@TempDir Path dir) throws IOException {
        Path file = writePlaintext(dir);
        try (Stream<String> lines = LogSources.openPlainLines(file)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipLinesStreamsEveryLine(@TempDir Path dir) throws IOException {
        Path file = writeGzip(dir);
        try (Stream<String> lines = LogSources.openGZipLines(file)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipLinesStreamsFirstEntry(@TempDir Path dir) throws IOException {
        Path file = writeZip(dir);
        try (Stream<String> lines = LogSources.openZipLines(file)) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    private static Path writePlaintext(Path dir) throws IOException {
        Path file = dir.resolve("gc.log");
        Files.writeString(file, LINE_CONTENT);
        return file;
    }

    private static Path writeGzip(Path dir) throws IOException {
        Path file = dir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.write(LINE_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static Path writeZip(Path dir) throws IOException {
        Path file = dir.resolve("gc.log.zip");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            zip.putNextEntry(new ZipEntry("gc.log"));
            zip.write(LINE_CONTENT.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }
}

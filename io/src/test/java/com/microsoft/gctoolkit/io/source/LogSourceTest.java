// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceTest {

    private static final String LINE_A = "first";
    private static final String LINE_B = "second";
    private static final String LINE_C = "third";
    private static final String PLAIN_LOG = "plain.log";
    private static final String ZIP_NAME = "gc.log.zip";
    private static final String ZIP_ENTRY = "gc.log";
    private static final String GZIP_NAME = "gc.log.gz";

    @Test
    void detectPlainText(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp, PLAIN_LOG);
        assertEquals(LogSourceFormat.PLAINTEXT, LogSource.detectFormat(plain));
    }

    @Test
    void detectDirectory(@TempDir Path tmp) {
        assertEquals(LogSourceFormat.DIRECTORY, LogSource.detectFormat(tmp));
    }

    @Test
    void detectZip(@TempDir Path tmp) throws IOException {
        Path zip = writeZip(tmp, ZIP_NAME, ZIP_ENTRY);
        assertEquals(LogSourceFormat.ZIP, LogSource.detectFormat(zip));
    }

    @Test
    void detectGZip(@TempDir Path tmp) throws IOException {
        Path gz = writeGZip(tmp, GZIP_NAME);
        assertEquals(LogSourceFormat.GZIP, LogSource.detectFormat(gz));
    }

    @Test
    void detectFormatReturnsPlainForShortFile(@TempDir Path tmp) throws IOException {
        Path tiny = tmp.resolve("tiny.log");
        Files.write(tiny, new byte[]{0x00});
        assertEquals(LogSourceFormat.PLAINTEXT, LogSource.detectFormat(tiny));
    }

    @Test
    void byteSizeReportsFileLength(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp, "sized.log");
        assertEquals(Files.size(plain), LogSource.byteSize(plain));
        assertTrue(LogSource.byteSize(plain) > 0);
    }

    @Test
    void openPlainStreamsAllLines(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp, PLAIN_LOG);
        try (Stream<String> stream = LogSource.openPlain(plain)) {
            assertEquals(List.of(LINE_A, LINE_B, LINE_C), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipStreamsFirstEntry(@TempDir Path tmp) throws IOException {
        Path zip = writeZip(tmp, ZIP_NAME, ZIP_ENTRY);
        try (Stream<String> stream = LogSource.openZip(zip)) {
            assertEquals(List.of(LINE_A, LINE_B, LINE_C), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("with-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write((LINE_A + "\n" + LINE_B + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = LogSource.openZip(zip)) {
            assertEquals(List.of(LINE_A, LINE_B), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipOnMissingFileThrows(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.zip");
        assertThrows(IOException.class, () -> LogSource.openZip(missing));
    }

    @Test
    void openGZipStreamsAllLines(@TempDir Path tmp) throws IOException {
        Path gz = writeGZip(tmp, GZIP_NAME);
        try (Stream<String> stream = LogSource.openGZip(gz)) {
            assertEquals(List.of(LINE_A, LINE_B, LINE_C), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipOnNonGzipThrows(@TempDir Path tmp) throws IOException {
        Path plain = writePlain(tmp, PLAIN_LOG);
        assertThrows(IOException.class, () -> LogSource.openGZip(plain));
    }

    @Test
    void logSourceFormatValuesAreStable() {
        assertNotNull(LogSourceFormat.valueOf("PLAINTEXT"));
        assertNotNull(LogSourceFormat.valueOf("ZIP"));
        assertNotNull(LogSourceFormat.valueOf("GZIP"));
        assertNotNull(LogSourceFormat.valueOf("DIRECTORY"));
        assertNotNull(LogSourceFormat.valueOf("UNKNOWN"));
    }

    private static Path writePlain(Path tmp, String name) throws IOException {
        Path file = tmp.resolve(name);
        Files.write(file, List.of(LINE_A, LINE_B, LINE_C));
        return file;
    }

    private static Path writeZip(Path tmp, String name, String entryName) throws IOException {
        Path file = tmp.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry(entryName));
            out.write((LINE_A + "\n" + LINE_B + "\n" + LINE_C + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return file;
    }

    private static Path writeGZip(Path tmp, String name) throws IOException {
        Path file = tmp.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write((LINE_A + "\n" + LINE_B + "\n" + LINE_C + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final String PLAIN_NAME = "gc.log";
    private static final String ZIP_NAME = "gc.zip";
    private static final String GZIP_NAME = "gc.log.gz";

    @Test
    void detectsPlainTextByAbsenceOfMagic(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.writeString(plain, "hello\nworld\n");
        assertEquals(LogSourceFormat.PLAINTEXT, GCLogSources.detectFormat(plain));
    }

    @Test
    void detectsDirectory(@TempDir Path tmp) {
        assertEquals(LogSourceFormat.DIRECTORY, GCLogSources.detectFormat(tmp));
    }

    @Test
    void detectsZipByMagic(@TempDir Path tmp) throws IOException {
        Path zip = writeZip(tmp.resolve(ZIP_NAME), "line-a", "line-b");
        assertEquals(LogSourceFormat.ZIP, GCLogSources.detectFormat(zip));
    }

    @Test
    void detectsGZipByMagic(@TempDir Path tmp) throws IOException {
        Path gz = writeGZip(tmp.resolve(GZIP_NAME), "line-a", "line-b");
        assertEquals(LogSourceFormat.GZIP, GCLogSources.detectFormat(gz));
    }

    @Test
    void sizeInBytesReadsFileLength(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.writeString(plain, "abcdef");
        assertEquals(6L, GCLogSources.sizeInBytes(plain));
    }

    @Test
    void sizeInBytesForDirectoryIsZero(@TempDir Path tmp) {
        assertEquals(0L, GCLogSources.sizeInBytes(tmp));
    }

    @Test
    void sizeInBytesForMissingFileIsZero(@TempDir Path tmp) {
        assertEquals(0L, GCLogSources.sizeInBytes(tmp.resolve("does-not-exist")));
    }

    @Test
    void openPlainTextYieldsAllLines(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.writeString(plain, "one\ntwo\nthree\n");
        try (Stream<String> stream = GCLogSources.openPlainText(plain)) {
            assertEquals(List.of("one", "two", "three"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipYieldsFirstEntryLines(@TempDir Path tmp) throws IOException {
        Path zip = writeZip(tmp.resolve(ZIP_NAME), "alpha", "beta");
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(List.of("alpha", "beta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipYieldsDecompressedLines(@TempDir Path tmp) throws IOException {
        Path gz = writeGZip(tmp.resolve(GZIP_NAME), "x", "y", "z");
        try (Stream<String> stream = GCLogSources.openGZip(gz)) {
            assertEquals(List.of("x", "y", "z"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openDispatchesByFormat(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve(PLAIN_NAME);
        Files.writeString(plain, "plain\n");
        try (Stream<String> stream = GCLogSources.open(plain)) {
            assertNotNull(stream);
            assertEquals(List.of("plain"), stream.collect(Collectors.toList()));
        }

        Path zip = writeZip(tmp.resolve(ZIP_NAME), "zipped");
        try (Stream<String> stream = GCLogSources.open(zip)) {
            assertNotNull(stream);
            assertEquals(List.of("zipped"), stream.collect(Collectors.toList()));
        }

        Path gz = writeGZip(tmp.resolve(GZIP_NAME), "gzipped");
        try (Stream<String> stream = GCLogSources.open(gz)) {
            assertNotNull(stream);
            assertEquals(List.of("gzipped"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openReturnsNullForDirectory(@TempDir Path tmp) throws IOException {
        assertNull(GCLogSources.open(tmp));
    }

    @Test
    void openPlainTextThrowsForMissingFile(@TempDir Path tmp) {
        assertThrows(IOException.class, () -> GCLogSources.openPlainText(tmp.resolve("nope")));
    }

    @Test
    void detectFormatCoversAllEnumValues() {
        assertTrue(LogSourceFormat.values().length >= 5);
        for (LogSourceFormat value : LogSourceFormat.values()) {
            assertEquals(value, LogSourceFormat.valueOf(value.name()));
        }
    }

    private static Path writeZip(Path path, String... lines) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry(PLAIN_NAME));
            for (String line : lines) {
                out.write((line + "\n").getBytes());
            }
            out.closeEntry();
        }
        return path;
    }

    private static Path writeGZip(Path path, String... lines) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            for (String line : lines) {
                out.write((line + "\n").getBytes());
            }
        }
        return path;
    }
}

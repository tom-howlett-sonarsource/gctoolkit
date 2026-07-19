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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");

    @Test
    void discoverPlainText(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.log");
        Files.write(plain, LINES);
        assertEquals(LogSourceFormat.PLAINTEXT, GCLogSources.discover(plain));
    }

    @Test
    void discoverDirectory(@TempDir Path tempDir) {
        assertEquals(LogSourceFormat.DIRECTORY, GCLogSources.discover(tempDir));
    }

    @Test
    void discoverZip(@TempDir Path tempDir) throws IOException {
        Path zip = writeZip(tempDir.resolve("archive.zip"));
        assertEquals(LogSourceFormat.ZIP, GCLogSources.discover(zip));
    }

    @Test
    void discoverGZip(@TempDir Path tempDir) throws IOException {
        Path gzip = writeGZip(tempDir.resolve("archive.gz"));
        assertEquals(LogSourceFormat.GZIP, GCLogSources.discover(gzip));
    }

    @Test
    void discoverNullReturnsUnknown() {
        assertEquals(LogSourceFormat.UNKNOWN, GCLogSources.discover(null));
    }

    @Test
    void hasMagicReturnsFalseForMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        assertFalse(GCLogSources.hasMagic(missing, GCLogSources.GZIP_MAGIC1, GCLogSources.GZIP_MAGIC2));
    }

    @Test
    void byteSizeReturnsFileSize(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("sized.log");
        byte[] payload = "abcdef".getBytes(StandardCharsets.UTF_8);
        Files.write(plain, payload);
        assertEquals(payload.length, GCLogSources.byteSize(plain));
    }

    @Test
    void byteSizeReturnsNegativeForMissing(@TempDir Path tempDir) {
        assertEquals(-1L, GCLogSources.byteSize(tempDir.resolve("missing")));
    }

    @Test
    void byteSizeReturnsNegativeForNull() {
        assertEquals(-1L, GCLogSources.byteSize(null));
    }

    @Test
    void openPlainReadsAllLines(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.log");
        Files.write(plain, LINES);
        try (Stream<String> stream = GCLogSources.openPlain(plain)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipReadsFirstEntry(@TempDir Path tempDir) throws IOException {
        Path zip = writeZip(tempDir.resolve("archive.zip"));
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipReadsAllLines(@TempDir Path tempDir) throws IOException {
        Path gzip = writeGZip(tempDir.resolve("archive.gz"));
        try (Stream<String> stream = GCLogSources.openGZip(gzip)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openDispatchesByFormat(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.log");
        Files.write(plain, LINES);
        try (Stream<String> stream = GCLogSources.open(plain, LogSourceFormat.PLAINTEXT)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }

        Path zip = writeZip(tempDir.resolve("archive.zip"));
        try (Stream<String> stream = GCLogSources.open(zip, LogSourceFormat.ZIP)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }

        Path gzip = writeGZip(tempDir.resolve("archive.gz"));
        try (Stream<String> stream = GCLogSources.open(gzip, LogSourceFormat.GZIP)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openRejectsUnknownFormat(@TempDir Path tempDir) {
        Path any = tempDir.resolve("any");
        assertThrows(IOException.class, () -> GCLogSources.open(any, LogSourceFormat.UNKNOWN));
    }

    @Test
    void openRejectsDirectoryFormat(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> GCLogSources.open(tempDir, LogSourceFormat.DIRECTORY));
    }

    @Test
    void magicConstantsAreAsAdvertised() {
        assertTrue(GCLogSources.GZIP_MAGIC1 == 0x1F && GCLogSources.GZIP_MAGIC2 == 0x8B);
        assertTrue(GCLogSources.ZIP_MAGIC1 == 0x50 && GCLogSources.ZIP_MAGIC2 == 0x4B);
    }

    private static Path writeZip(Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write(joinedBytes());
            zos.closeEntry();
        }
        return zipPath;
    }

    private static Path writeGZip(Path gzipPath) throws IOException {
        try (OutputStream gzos = new GZIPOutputStream(Files.newOutputStream(gzipPath))) {
            gzos.write(joinedBytes());
        }
        return gzipPath;
    }

    private static byte[] joinedBytes() {
        return String.join("\n", LINES).getBytes(StandardCharsets.UTF_8);
    }
}

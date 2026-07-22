// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

class GCLogSourceTest {

    private static final List<String> EXPECTED_LINES = List.of("line1", "line2", "line3");
    private static final String CONTENT = "line1\nline2\nline3\n";

    @TempDir
    Path tempDir;

    private Path plainFile;
    private Path gzipFile;
    private Path zipFile;
    private Path zipWithDirEntryFile;

    @BeforeEach
    void setUp() throws IOException {
        plainFile = tempDir.resolve("test.log");
        Files.write(plainFile, EXPECTED_LINES);

        gzipFile = tempDir.resolve("test.log.gz");
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(gzipFile))) {
            gzos.write(CONTENT.getBytes());
        }

        zipFile = tempDir.resolve("test.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("test.log"));
            zos.write(CONTENT.getBytes());
            zos.closeEntry();
        }

        zipWithDirEntryFile = tempDir.resolve("test_dir.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipWithDirEntryFile))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/test.log"));
            zos.write(CONTENT.getBytes());
            zos.closeEntry();
        }
    }

    @Test
    void detectFormatPlainText() {
        assertEquals(GCLogSource.Format.PLAINTEXT, GCLogSource.detectFormat(plainFile));
    }

    @Test
    void detectFormatGZip() {
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.detectFormat(gzipFile));
    }

    @Test
    void detectFormatZip() {
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.detectFormat(zipFile));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.detectFormat(tempDir));
    }

    @Test
    void streamPlainText() throws IOException {
        try (Stream<String> stream = GCLogSource.streamPlainText(plainFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamGZip() throws IOException {
        try (Stream<String> stream = GCLogSource.streamGZip(gzipFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamZip() throws IOException {
        try (Stream<String> stream = GCLogSource.streamZip(zipFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamZipSkipsDirectoryEntries() throws IOException {
        try (Stream<String> stream = GCLogSource.streamZip(zipWithDirEntryFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamAutoDetectsPlainText() throws IOException {
        try (Stream<String> stream = GCLogSource.stream(plainFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamAutoDetectsGZip() throws IOException {
        try (Stream<String> stream = GCLogSource.stream(gzipFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamAutoDetectsZip() throws IOException {
        try (Stream<String> stream = GCLogSource.stream(zipFile)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamThrowsForDirectory() {
        assertThrows(IOException.class, () -> GCLogSource.stream(tempDir));
    }

    @Test
    void byteSizeReturnsFileSize() throws IOException {
        long expected = Files.size(plainFile);
        assertEquals(expected, GCLogSource.byteSize(plainFile));
        assertTrue(expected > 0);
    }

    @Test
    void byteSizeWorksForCompressedFiles() throws IOException {
        assertTrue(GCLogSource.byteSize(gzipFile) > 0);
        assertTrue(GCLogSource.byteSize(zipFile) > 0);
    }

    @Test
    void streamWithExplicitFormat() throws IOException {
        try (Stream<String> stream = GCLogSource.stream(plainFile, GCLogSource.Format.PLAINTEXT)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
        try (Stream<String> stream = GCLogSource.stream(gzipFile, GCLogSource.Format.GZIP)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
        try (Stream<String> stream = GCLogSource.stream(zipFile, GCLogSource.Format.ZIP)) {
            assertEquals(EXPECTED_LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void streamWithUnknownFormatThrows() {
        assertThrows(IOException.class, () -> GCLogSource.stream(plainFile, GCLogSource.Format.UNKNOWN));
    }

    @Test
    void detectFormatReturnsPlainTextForNonexistentFile() {
        Path nonexistent = tempDir.resolve("does_not_exist.log");
        assertEquals(GCLogSource.Format.PLAINTEXT, GCLogSource.detectFormat(nonexistent));
    }
}

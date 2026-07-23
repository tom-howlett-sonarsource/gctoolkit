// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.core;

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

class GCLogSourcesTest {

    private static final String LINE1 = "line1";
    private static final String LINE2 = "line2";
    private static final String LINE3 = "line3";
    private static final String CONTENT = LINE1 + "\n" + LINE2 + "\n" + LINE3 + "\n";

    @Test
    void detectFormatPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, CONTENT);

        assertEquals(FileFormat.PLAINTEXT, GCLogSources.detectFormat(file));
    }

    @Test
    void detectFormatDirectory(@TempDir Path tempDir) {
        assertEquals(FileFormat.DIRECTORY, GCLogSources.detectFormat(tempDir));
    }

    @Test
    void detectFormatGzip(@TempDir Path tempDir) throws IOException {
        Path gzFile = createGzipFile(tempDir, "test.log.gz", CONTENT);

        assertEquals(FileFormat.GZIP, GCLogSources.detectFormat(gzFile));
    }

    @Test
    void detectFormatZip(@TempDir Path tempDir) throws IOException {
        Path zipFile = createZipFile(tempDir, "test.log.zip", "test.log", CONTENT);

        assertEquals(FileFormat.ZIP, GCLogSources.detectFormat(zipFile));
    }

    @Test
    void streamPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, CONTENT);

        try (Stream<String> stream = GCLogSources.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE1, LINE2, LINE3), lines);
        }
    }

    @Test
    void streamGZipFile(@TempDir Path tempDir) throws IOException {
        Path gzFile = createGzipFile(tempDir, "test.log.gz", CONTENT);

        try (Stream<String> stream = GCLogSources.streamGZipFile(gzFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE1, LINE2, LINE3), lines);
        }
    }

    @Test
    void streamZipFile(@TempDir Path tempDir) throws IOException {
        Path zipFile = createZipFile(tempDir, "test.log.zip", "test.log", CONTENT);

        try (Stream<String> stream = GCLogSources.streamZipFile(zipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE1, LINE2, LINE3), lines);
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/test.log"));
            zos.write(CONTENT.getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GCLogSources.streamZipFile(zipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE1, LINE2, LINE3), lines);
        }
    }

    @Test
    void fileSize(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, CONTENT);

        long size = GCLogSources.fileSize(file);
        assertEquals(Files.size(file), size);
        assertTrue(size > 0);
    }

    @Test
    void fileSizeThrowsForMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.log");
        assertThrows(IOException.class, () -> GCLogSources.fileSize(missing));
    }

    @Test
    void streamPlainTextThrowsForMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.log");
        assertThrows(IOException.class, () -> GCLogSources.streamPlainText(missing));
    }

    @Test
    void detectFormatEmptyFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("empty.log");
        Files.writeString(file, "");

        assertEquals(FileFormat.PLAINTEXT, GCLogSources.detectFormat(file));
    }

    private static Path createGzipFile(Path dir, String name, String content) throws IOException {
        Path gzFile = dir.resolve(name);
        try (GZIPOutputStream gzos = new GZIPOutputStream(Files.newOutputStream(gzFile))) {
            gzos.write(content.getBytes());
        }
        return gzFile;
    }

    private static Path createZipFile(Path dir, String zipName, String entryName, String content) throws IOException {
        Path zipFile = dir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return zipFile;
    }
}

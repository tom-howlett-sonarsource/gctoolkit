// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
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

class GCLogSourceUtilTest {

    private static final String GC_LOG = "gc.log";

    @TempDir
    Path tempDir;

    @Test
    void detectFormatPlainText() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.write(plain, List.of("[0.001s] GC pause", "[0.002s] GC pause"));
        assertEquals(FileFormat.PLAINTEXT, GCLogSourceUtil.detectFormat(plain));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(FileFormat.DIRECTORY, GCLogSourceUtil.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path zipPath = createZipFile(GC_LOG, "[0.001s] GC pause\n[0.002s] GC pause\n");
        assertEquals(FileFormat.ZIP, GCLogSourceUtil.detectFormat(zipPath));
    }

    @Test
    void detectFormatGzip() throws IOException {
        Path gzipPath = createGzipFile("[0.001s] GC pause\n[0.002s] GC pause\n");
        assertEquals(FileFormat.GZIP, GCLogSourceUtil.detectFormat(gzipPath));
    }

    @Test
    void streamPlainText() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.write(plain, List.of("line1", "line2", "line3"));
        try (Stream<String> stream = GCLogSourceUtil.streamPlainText(plain)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("line1", lines.get(0));
            assertEquals("line3", lines.get(2));
        }
    }

    @Test
    void streamZipFile() throws IOException {
        Path zipPath = createZipFile(GC_LOG, "zipLine1\nzipLine2\n");
        try (Stream<String> stream = GCLogSourceUtil.streamZipFile(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("zipLine1", lines.get(0));
            assertEquals("zipLine2", lines.get(1));
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries() throws IOException {
        Path zipPath = tempDir.resolve("gc.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write("dirZipLine1\ndirZipLine2\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GCLogSourceUtil.streamZipFile(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("dirZipLine1", lines.get(0));
        }
    }

    @Test
    void streamZipFileThrowsOnEmpty() throws IOException {
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // empty zip, no entries
        }
        assertThrows(IOException.class, () -> GCLogSourceUtil.streamZipFile(zipPath));
    }

    @Test
    void streamGZipFile() throws IOException {
        Path gzipPath = createGzipFile("gzLine1\ngzLine2\ngzLine3\n");
        try (Stream<String> stream = GCLogSourceUtil.streamGZipFile(gzipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("gzLine1", lines.get(0));
            assertEquals("gzLine3", lines.get(2));
        }
    }

    @Test
    void sizeInBytes() throws IOException {
        Path plain = tempDir.resolve("sized.log");
        byte[] content = "hello world\n".getBytes();
        Files.write(plain, content);
        assertEquals(content.length, GCLogSourceUtil.sizeInBytes(plain));
    }

    @Test
    void sizeInBytesNonExistent() {
        Path missing = tempDir.resolve("does-not-exist.log");
        assertEquals(-1L, GCLogSourceUtil.sizeInBytes(missing));
    }

    @Test
    void discoverLogFilesAll() throws IOException {
        Files.write(tempDir.resolve("gc.log.0"), List.of("a"));
        Files.write(tempDir.resolve("gc.log.1"), List.of("b"));
        Files.write(tempDir.resolve("other.txt"), List.of("c"));
        List<Path> all = GCLogSourceUtil.discoverLogFiles(tempDir, null);
        assertEquals(3, all.size());
    }

    @Test
    void discoverLogFilesFiltered() throws IOException {
        Files.write(tempDir.resolve("gc.log.0"), List.of("a"));
        Files.write(tempDir.resolve("gc.log.1"), List.of("b"));
        Files.write(tempDir.resolve("other.txt"), List.of("c"));
        List<Path> filtered = GCLogSourceUtil.discoverLogFiles(tempDir, GC_LOG);
        assertEquals(2, filtered.size());
        assertTrue(filtered.stream().allMatch(p -> p.getFileName().toString().startsWith(GC_LOG)));
    }

    private Path createZipFile(String entryName, String content) throws IOException {
        Path zipPath = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }

    private Path createGzipFile(String content) throws IOException {
        Path gzipPath = tempDir.resolve("test.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzipPath.toFile()))) {
            gos.write(content.getBytes());
        }
        return gzipPath;
    }
}

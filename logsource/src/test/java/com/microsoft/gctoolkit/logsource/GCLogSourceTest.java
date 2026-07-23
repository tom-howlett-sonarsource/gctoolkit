// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    @TempDir
    Path tempDir;

    private Path createPlainTextFile(String... lines) throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.write(file, List.of(lines));
        return file;
    }

    private Path createGzipFile(String... lines) throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (OutputStream fos = Files.newOutputStream(file);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            for (String line : lines) {
                gzos.write((line + "\n").getBytes());
            }
        }
        return file;
    }

    private Path createZipFile(String... lines) throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (OutputStream fos = Files.newOutputStream(file);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            for (String line : lines) {
                zos.write((line + "\n").getBytes());
            }
            zos.closeEntry();
        }
        return file;
    }

    // ---- Format detection tests -------------------------------------------

    @Test
    void detectPlainText() throws IOException {
        Path file = createPlainTextFile("[0.001s] GC(0) Pause Young");
        assertEquals(GCLogSource.Format.PLAINTEXT, GCLogSource.detect(file));
    }

    @Test
    void detectGzip() throws IOException {
        Path file = createGzipFile("[0.001s] GC(0) Pause Young");
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.detect(file));
    }

    @Test
    void detectZip() throws IOException {
        Path file = createZipFile("[0.001s] GC(0) Pause Young");
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.detect(file));
    }

    @Test
    void detectDirectory() {
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.detect(tempDir));
    }

    // ---- Stream opening tests --------------------------------------------

    @Test
    void streamPlainText() throws IOException {
        Path file = createPlainTextFile("line1", "line2", "line3");
        try (Stream<String> stream = GCLogSource.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("line1", lines.get(0));
            assertEquals("line2", lines.get(1));
            assertEquals("line3", lines.get(2));
        }
    }

    @Test
    void streamGzipFile() throws IOException {
        Path file = createGzipFile("gzip-line1", "gzip-line2");
        try (Stream<String> stream = GCLogSource.streamGZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("gzip-line1", lines.get(0));
            assertEquals("gzip-line2", lines.get(1));
        }
    }

    @Test
    void streamZipFile() throws IOException {
        Path file = createZipFile("zip-line1", "zip-line2", "zip-line3");
        try (Stream<String> stream = GCLogSource.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("zip-line1", lines.get(0));
            assertEquals("zip-line2", lines.get(1));
            assertEquals("zip-line3", lines.get(2));
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("gc-with-dir.zip");
        try (OutputStream fos = Files.newOutputStream(file);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write("actual-content\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GCLogSource.streamZipFile(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("actual-content", lines.get(0));
        }
    }

    // ---- Auto-detect stream tests ----------------------------------------

    @Test
    void streamAutoDetectsPlainText() throws IOException {
        Path file = createPlainTextFile("auto-plain");
        try (Stream<String> stream = GCLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("auto-plain", lines.get(0));
        }
    }

    @Test
    void streamAutoDetectsGzip() throws IOException {
        Path file = createGzipFile("auto-gzip");
        try (Stream<String> stream = GCLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("auto-gzip", lines.get(0));
        }
    }

    @Test
    void streamAutoDetectsZip() throws IOException {
        Path file = createZipFile("auto-zip");
        try (Stream<String> stream = GCLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("auto-zip", lines.get(0));
        }
    }

    @Test
    void streamThrowsForDirectory() {
        assertThrows(IOException.class, () -> GCLogSource.stream(tempDir));
    }

    // ---- Byte sizing tests -----------------------------------------------

    @Test
    void sizeReturnsFileSize() throws IOException {
        byte[] content = "hello world\n".getBytes();
        Path file = tempDir.resolve("sized.log");
        Files.write(file, content);
        assertEquals(content.length, GCLogSource.size(file));
    }

    @Test
    void sizeThrowsForMissingFile() {
        Path missing = tempDir.resolve("does-not-exist.log");
        assertThrows(IOException.class, () -> GCLogSource.size(missing));
    }
}

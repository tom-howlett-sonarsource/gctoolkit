// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.logsource;

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

class LogSourceHelperTest {

    private static final String GC_LOG = "gc.log";
    private static final String NONEXISTENT = "nonexistent";

    @TempDir
    Path tempDir;

    @Test
    void detectFormatPlainText() throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, "some gc log content\n");
        assertEquals(FileFormat.PLAINTEXT, LogSourceHelper.detectFormat(file));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(FileFormat.DIRECTORY, LogSourceHelper.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path zipFile = createZipFile(GC_LOG, "line1\nline2\n");
        assertEquals(FileFormat.ZIP, LogSourceHelper.detectFormat(zipFile));
    }

    @Test
    void detectFormatGzip() throws IOException {
        Path gzipFile = createGzipFile("line1\nline2\n");
        assertEquals(FileFormat.GZIP, LogSourceHelper.detectFormat(gzipFile));
    }

    @Test
    void detectFormatUnknownForNonexistent() {
        Path missing = tempDir.resolve(NONEXISTENT);
        assertEquals(FileFormat.UNKNOWN, LogSourceHelper.detectFormat(missing));
    }

    @Test
    void fileSizeReturnsCorrectValue() throws IOException {
        Path file = tempDir.resolve("sized.log");
        String content = "hello world";
        Files.writeString(file, content);
        assertEquals(Files.size(file), LogSourceHelper.fileSize(file));
    }

    @Test
    void fileSizeThrowsForNonexistent() {
        Path missing = tempDir.resolve(NONEXISTENT);
        assertThrows(IOException.class, () -> LogSourceHelper.fileSize(missing));
    }

    @Test
    void streamPlainTextReturnsLines() throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, "line1\nline2\nline3\n");
        try (Stream<String> stream = LogSourceHelper.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("line1", lines.get(0));
            assertEquals("line2", lines.get(1));
            assertEquals("line3", lines.get(2));
        }
    }

    @Test
    void streamZipFileReturnsLines() throws IOException {
        Path zipFile = createZipFile(GC_LOG, "zip-line1\nzip-line2\n");
        try (Stream<String> stream = LogSourceHelper.streamZipFile(zipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("zip-line1", lines.get(0));
            assertEquals("zip-line2", lines.get(1));
        }
    }

    @Test
    void streamZipFileSkipsDirectoryEntries() throws IOException {
        Path zipFile = tempDir.resolve("withdir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            zos.putNextEntry(new ZipEntry("somedir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("somedir/gc.log"));
            zos.write("nested-line\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = LogSourceHelper.streamZipFile(zipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("nested-line", lines.get(0));
        }
    }

    @Test
    void streamZipFileThrowsForEmptyZip() throws IOException {
        Path zipFile = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            // empty zip, no entries
        }
        assertThrows(IOException.class, () -> LogSourceHelper.streamZipFile(zipFile));
    }

    @Test
    void streamGZipFileReturnsLines() throws IOException {
        Path gzipFile = createGzipFile("gz-line1\ngz-line2\ngz-line3\n");
        try (Stream<String> stream = LogSourceHelper.streamGZipFile(gzipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("gz-line1", lines.get(0));
            assertEquals("gz-line2", lines.get(1));
            assertEquals("gz-line3", lines.get(2));
        }
    }

    @Test
    void streamAutoDetectsPlainText() throws IOException {
        Path file = tempDir.resolve("auto.log");
        Files.writeString(file, "auto-line\n");
        try (Stream<String> stream = LogSourceHelper.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("auto-line", lines.get(0));
        }
    }

    @Test
    void streamAutoDetectsZip() throws IOException {
        Path zipFile = createZipFile("auto.log", "auto-zip\n");
        try (Stream<String> stream = LogSourceHelper.stream(zipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("auto-zip", lines.get(0));
        }
    }

    @Test
    void streamAutoDetectsGzip() throws IOException {
        Path gzipFile = createGzipFile("auto-gz\n");
        try (Stream<String> stream = LogSourceHelper.stream(gzipFile)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("auto-gz", lines.get(0));
        }
    }

    @Test
    void streamThrowsForDirectory() {
        assertThrows(IOException.class, () -> LogSourceHelper.stream(tempDir));
    }

    @Test
    void streamThrowsForUnknownFormat() {
        Path missing = tempDir.resolve(NONEXISTENT);
        assertThrows(IOException.class, () -> LogSourceHelper.stream(missing));
    }

    private Path createZipFile(String entryName, String content) throws IOException {
        Path zipFile = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return zipFile;
    }

    private Path createGzipFile(String content) throws IOException {
        Path gzipFile = tempDir.resolve("test.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzipFile.toFile()))) {
            gos.write(content.getBytes());
        }
        return gzipFile;
    }
}

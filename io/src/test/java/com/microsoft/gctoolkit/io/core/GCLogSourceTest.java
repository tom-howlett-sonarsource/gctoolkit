// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
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

class GCLogSourceTest {

    private static final String GC_LOG_ENTRY = "gc.log";
    private static final String UNIFIED_LOG_LINE = "[0.011s][info][gc] Using G1\n";

    @TempDir
    Path tempDir;

    // --- detectFormat ---

    @Test
    void detectFormatPlainText() throws IOException {
        Path plain = tempDir.resolve(GC_LOG_ENTRY);
        Files.writeString(plain, UNIFIED_LOG_LINE);
        assertEquals(FileFormat.PLAINTEXT, GCLogSource.detectFormat(plain));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(FileFormat.DIRECTORY, GCLogSource.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path zip = createZipFile(GC_LOG_ENTRY, UNIFIED_LOG_LINE);
        assertEquals(FileFormat.ZIP, GCLogSource.detectFormat(zip));
    }

    @Test
    void detectFormatGZip() throws IOException {
        Path gzip = createGZipFile(UNIFIED_LOG_LINE);
        assertEquals(FileFormat.GZIP, GCLogSource.detectFormat(gzip));
    }

    // --- byteSize ---

    @Test
    void byteSizeReturnsCorrectSize() throws IOException {
        String content = "line one\nline two\n";
        Path file = tempDir.resolve("sized.log");
        Files.writeString(file, content);
        assertEquals(content.getBytes().length, GCLogSource.byteSize(file));
    }

    // --- openPlainTextStream ---

    @Test
    void openPlainTextStreamReadsLines() throws IOException {
        Path file = tempDir.resolve("plain.log");
        Files.writeString(file, "line1\nline2\nline3\n");
        try (Stream<String> stream = GCLogSource.openPlainTextStream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("line1", lines.get(0));
            assertEquals("line3", lines.get(2));
        }
    }

    // --- openZipStream ---

    @Test
    void openZipStreamReadsFirstEntry() throws IOException {
        Path zip = createZipFile(GC_LOG_ENTRY, "alpha\nbeta\n");
        try (Stream<String> stream = GCLogSource.openZipStream(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("alpha", lines.get(0));
            assertEquals("beta", lines.get(1));
        }
    }

    @Test
    void openZipStreamSkipsDirectoryEntries() throws IOException {
        Path zip = tempDir.resolve("withdir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write("gamma\ndelta\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GCLogSource.openZipStream(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("gamma", lines.get(0));
        }
    }

    // --- openGZipStream ---

    @Test
    void openGZipStreamReadsLines() throws IOException {
        Path gzip = createGZipFile("first\nsecond\n");
        try (Stream<String> stream = GCLogSource.openGZipStream(gzip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("first", lines.get(0));
            assertEquals("second", lines.get(1));
        }
    }

    // --- openStream ---

    @Test
    void openStreamDispatchesPlainText() throws IOException {
        Path file = tempDir.resolve("dispatch.log");
        Files.writeString(file, "hello\n");
        try (Stream<String> stream = GCLogSource.openStream(file, FileFormat.PLAINTEXT)) {
            assertEquals("hello", stream.findFirst().orElse(""));
        }
    }

    @Test
    void openStreamDispatchesZip() throws IOException {
        Path zip = createZipFile("test.log", "zipped\n");
        try (Stream<String> stream = GCLogSource.openStream(zip, FileFormat.ZIP)) {
            assertEquals("zipped", stream.findFirst().orElse(""));
        }
    }

    @Test
    void openStreamDispatchesGZip() throws IOException {
        Path gzip = createGZipFile("compressed\n");
        try (Stream<String> stream = GCLogSource.openStream(gzip, FileFormat.GZIP)) {
            assertEquals("compressed", stream.findFirst().orElse(""));
        }
    }

    @Test
    void openStreamThrowsForDirectory() {
        assertThrows(IOException.class, () -> GCLogSource.openStream(tempDir, FileFormat.DIRECTORY));
    }

    @Test
    void openStreamThrowsForUnknown() {
        assertThrows(IOException.class, () -> GCLogSource.openStream(tempDir, FileFormat.UNKNOWN));
    }

    // --- detectFormat + openStream round-trip ---

    @Test
    void detectAndOpenRoundTripPlainText() throws IOException {
        Path file = tempDir.resolve("roundtrip.log");
        Files.writeString(file, "content\n");
        FileFormat format = GCLogSource.detectFormat(file);
        try (Stream<String> stream = GCLogSource.openStream(file, format)) {
            assertEquals("content", stream.findFirst().orElse(""));
        }
    }

    @Test
    void detectAndOpenRoundTripZip() throws IOException {
        Path zip = createZipFile("rt.log", "zip-content\n");
        FileFormat format = GCLogSource.detectFormat(zip);
        try (Stream<String> stream = GCLogSource.openStream(zip, format)) {
            assertEquals("zip-content", stream.findFirst().orElse(""));
        }
    }

    @Test
    void detectAndOpenRoundTripGZip() throws IOException {
        Path gzip = createGZipFile("gz-content\n");
        FileFormat format = GCLogSource.detectFormat(gzip);
        try (Stream<String> stream = GCLogSource.openStream(gzip, format)) {
            assertEquals("gz-content", stream.findFirst().orElse(""));
        }
    }

    @Test
    void detectFormatEmptyFileReturnPlainText() throws IOException {
        Path empty = tempDir.resolve("empty.log");
        Files.writeString(empty, "");
        assertEquals(FileFormat.PLAINTEXT, GCLogSource.detectFormat(empty));
    }

    @Test
    void byteSizeEmptyFile() throws IOException {
        Path empty = tempDir.resolve("empty-size.log");
        Files.writeString(empty, "");
        assertEquals(0, GCLogSource.byteSize(empty));
    }

    @Test
    void byteSizeNonExistentFileThrows() {
        Path missing = tempDir.resolve("missing.log");
        assertThrows(IOException.class, () -> GCLogSource.byteSize(missing));
    }

    // --- helpers ---

    private Path createZipFile(String entryName, String content) throws IOException {
        Path zip = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return zip;
    }

    private Path createGZipFile(String content) throws IOException {
        Path gzip = tempDir.resolve("test.gz");
        try (OutputStream os = new GZIPOutputStream(new FileOutputStream(gzip.toFile()))) {
            os.write(content.getBytes());
        }
        return gzip;
    }
}

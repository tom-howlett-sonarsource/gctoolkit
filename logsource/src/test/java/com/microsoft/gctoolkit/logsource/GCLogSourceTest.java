// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String LINE_1 = "[0.011s][info][gc] Using G1";
    private static final String LINE_2 = "[0.012s][info][gc] Heap region size: 1M";
    private static final String GC_LOG = "gc.log";
    private static final String GC_LOG_ZIP = "gc.log.zip";
    private static final String GC_LOG_GZ = "gc.log.gz";

    @Test
    void detectFormatPlainText(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n");

        assertEquals(FileFormat.PLAINTEXT, GCLogSource.detectFormat(plain));
    }

    @Test
    void detectFormatDirectory(@TempDir Path tempDir) {
        assertEquals(FileFormat.DIRECTORY, GCLogSource.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip(@TempDir Path tempDir) throws IOException {
        Path zipPath = createZipFile(tempDir, GC_LOG_ZIP, GC_LOG, LINE_1 + "\n" + LINE_2 + "\n");

        assertEquals(FileFormat.ZIP, GCLogSource.detectFormat(zipPath));
    }

    @Test
    void detectFormatGZip(@TempDir Path tempDir) throws IOException {
        Path gzipPath = createGZipFile(tempDir, GC_LOG_GZ, LINE_1 + "\n" + LINE_2 + "\n");

        assertEquals(FileFormat.GZIP, GCLogSource.detectFormat(gzipPath));
    }

    @Test
    void streamPlainText(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n" + LINE_2 + "\n");

        try (Stream<String> stream = GCLogSource.streamPlainText(plain)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void streamZip(@TempDir Path tempDir) throws IOException {
        Path zipPath = createZipFile(tempDir, GC_LOG_ZIP, GC_LOG, LINE_1 + "\n" + LINE_2 + "\n");

        try (Stream<String> stream = GCLogSource.streamZip(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void streamZipSkipsDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path zipPath = tempDir.resolve(GC_LOG_ZIP);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GCLogSource.streamZip(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void streamGZip(@TempDir Path tempDir) throws IOException {
        Path gzipPath = createGZipFile(tempDir, GC_LOG_GZ, LINE_1 + "\n" + LINE_2 + "\n");

        try (Stream<String> stream = GCLogSource.streamGZip(gzipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void streamAutoDetectsPlain(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n");

        try (Stream<String> stream = GCLogSource.stream(plain)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void streamAutoDetectsZip(@TempDir Path tempDir) throws IOException {
        Path zipPath = createZipFile(tempDir, "archive.zip", GC_LOG, LINE_1 + "\n");

        try (Stream<String> stream = GCLogSource.stream(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void streamAutoDetectsGZip(@TempDir Path tempDir) throws IOException {
        Path gzipPath = createGZipFile(tempDir, GC_LOG_GZ, LINE_1 + "\n");

        try (Stream<String> stream = GCLogSource.stream(gzipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void streamDirectoryThrows(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> GCLogSource.stream(tempDir));
    }

    @Test
    void byteCountReturnsFileSize(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        byte[] content = (LINE_1 + "\n").getBytes();
        Files.write(plain, content);

        assertEquals(content.length, GCLogSource.byteCount(plain));
    }

    @Test
    void streamZipEmptyArchiveThrows(@TempDir Path tempDir) throws IOException {
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            // empty archive — no entries
        }

        assertThrows(IOException.class, () -> GCLogSource.streamZip(zipPath));
    }

    @Test
    void detectFormatEmptyFileReturnsPlainText(@TempDir Path tempDir) throws IOException {
        Path empty = tempDir.resolve("empty.log");
        Files.writeString(empty, "");

        assertEquals(FileFormat.PLAINTEXT, GCLogSource.detectFormat(empty));
    }

    @Test
    void streamWithRealGCLog() throws IOException {
        Path logPath = Path.of("../gclogs/unified/g1gc/minimal.log");
        if (!Files.exists(logPath)) {
            return; // skip if test data not available
        }
        try (Stream<String> stream = GCLogSource.stream(logPath)) {
            long count = stream.count();
            assertTrue(count > 0, "Expected at least one line from a real GC log");
        }
    }

    private static Path createZipFile(Path dir, String zipName, String entryName, String content) throws IOException {
        Path zipPath = dir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }

    private static Path createGZipFile(Path dir, String gzipName, String content) throws IOException {
        Path gzipPath = dir.resolve(gzipName);
        try (OutputStream fos = Files.newOutputStream(gzipPath);
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            gzos.write(content.getBytes());
        }
        return gzipPath;
    }
}

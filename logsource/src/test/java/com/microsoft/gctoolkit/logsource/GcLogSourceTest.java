// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcLogSourceTest {

    private static final String LINE_1 = "[0.011s][info][gc] Using G1";
    private static final String LINE_2 = "[0.022s][info][gc] Heap region size: 1M";
    private static final String CONTENT = LINE_1 + "\n" + LINE_2 + "\n";
    private static final String GC_LOG = "gc.log";
    private static final String GC_LOG_0 = "gc.log.0";
    private static final String GC_LOG_1 = "gc.log.1";
    private static final String LOGS_ZIP = "logs.zip";

    @Test
    void detectPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, CONTENT);

        assertEquals(LogSourceFormat.PLAINTEXT, GcLogSource.detectFormat(file));
    }

    @Test
    void detectDirectory(@TempDir Path tempDir) {
        assertEquals(LogSourceFormat.DIRECTORY, GcLogSource.detectFormat(tempDir));
    }

    @Test
    void detectGzip(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.write(CONTENT.getBytes());
        }

        assertEquals(LogSourceFormat.GZIP, GcLogSource.detectFormat(file));
    }

    @Test
    void detectZip(@TempDir Path tempDir) throws IOException {
        Path file = createZipFile(tempDir, "gc.log.zip", GC_LOG, CONTENT);

        assertEquals(LogSourceFormat.ZIP, GcLogSource.detectFormat(file));
    }

    @Test
    void streamPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, CONTENT);

        try (Stream<String> stream = GcLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void streamGzip(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.write(CONTENT.getBytes());
        }

        try (Stream<String> stream = GcLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void streamZip(@TempDir Path tempDir) throws IOException {
        Path file = createZipFile(tempDir, "gc.log.zip", GC_LOG, CONTENT);

        try (Stream<String> stream = GcLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void streamWithExplicitFormat(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, CONTENT);

        try (Stream<String> stream = GcLogSource.stream(file, LogSourceFormat.PLAINTEXT)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
        }
    }

    @Test
    void streamDirectoryThrows(@TempDir Path tempDir) {
        assertThrows(IOException.class, () -> GcLogSource.stream(tempDir, LogSourceFormat.DIRECTORY));
    }

    @Test
    void streamUnknownThrows(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, CONTENT);

        assertThrows(IOException.class, () -> GcLogSource.stream(file, LogSourceFormat.UNKNOWN));
    }

    @Test
    void sizeInBytesForFile(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(GC_LOG);
        Files.writeString(file, CONTENT);

        assertEquals(Files.size(file), GcLogSource.sizeInBytes(file));
    }

    @Test
    void sizeInBytesForDirectory(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve(GC_LOG_0);
        Path file2 = tempDir.resolve(GC_LOG_1);
        Files.writeString(file1, LINE_1);
        Files.writeString(file2, LINE_2);

        long expected = Files.size(file1) + Files.size(file2);
        assertEquals(expected, GcLogSource.sizeInBytes(tempDir));
    }

    @Test
    void listZipEntries(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(LOGS_ZIP);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(GC_LOG_0));
            zos.write(LINE_1.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(GC_LOG_1));
            zos.write(LINE_2.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("subdir/"));
            zos.closeEntry();
        }

        List<String> entries = GcLogSource.listZipEntries(file);
        assertEquals(2, entries.size());
        assertTrue(entries.contains(GC_LOG_0));
        assertTrue(entries.contains(GC_LOG_1));
    }

    @Test
    void streamZipEntry(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve(LOGS_ZIP);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(GC_LOG_0));
            zos.write(CONTENT.getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GcLogSource.streamZipEntry(file, GC_LOG_0)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void streamZipEntryMissingThrows(@TempDir Path tempDir) throws IOException {
        Path file = createZipFile(tempDir, LOGS_ZIP, GC_LOG, CONTENT);

        assertThrows(IOException.class, () -> GcLogSource.streamZipEntry(file, "nonexistent"));
    }

    @Test
    void discoverRotatingLogFilesInDirectory(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve(GC_LOG_0), LINE_1);
        Files.writeString(tempDir.resolve(GC_LOG_1), LINE_2);
        Files.createDirectory(tempDir.resolve("subdir"));

        List<Path> found = GcLogSource.discoverRotatingLogFiles(tempDir);
        assertEquals(2, found.size());
    }

    @Test
    void discoverRotatingLogFilesByFile(@TempDir Path tempDir) throws IOException {
        Path file0 = tempDir.resolve(GC_LOG_0);
        Path file1 = tempDir.resolve(GC_LOG_1);
        Path unrelated = tempDir.resolve("other.txt");
        Files.writeString(file0, LINE_1);
        Files.writeString(file1, LINE_2);
        Files.writeString(unrelated, "unrelated");

        List<Path> found = GcLogSource.discoverRotatingLogFiles(file0);
        assertEquals(2, found.size());
        assertFalse(found.contains(unrelated));
    }

    @Test
    void extractRootPatternSimple() {
        assertEquals(GC_LOG, GcLogSource.extractRootPattern(Path.of(GC_LOG_0)));
    }

    @Test
    void extractRootPatternCurrent() {
        assertEquals(GC_LOG, GcLogSource.extractRootPattern(Path.of("gc.log.0.current")));
    }

    @Test
    void extractRootPatternPlain() {
        assertEquals(GC_LOG, GcLogSource.extractRootPattern(Path.of(GC_LOG)));
    }

    @Test
    void zipStreamSkipsDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("with_dirs.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write(CONTENT.getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GcLogSource.stream(file, LogSourceFormat.ZIP)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    private static Path createZipFile(Path dir, String zipName, String entryName, String content) throws IOException {
        Path file = dir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return file;
    }
}

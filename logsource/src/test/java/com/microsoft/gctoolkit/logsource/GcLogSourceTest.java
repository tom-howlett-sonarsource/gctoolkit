// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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

class GcLogSourceTest {

    private static final String GC_LOG = "gc.log";
    private static final String GC_LOG_0 = "gc.log.0";
    private static final String GC_LOG_1 = "gc.log.1";
    private static final String ZIP_DIR_PREFIX = "logs/";

    @TempDir
    Path tempDir;

    @Test
    void detectFormatPlainText() throws IOException {
        Path file = tempDir.resolve("test.log");
        Files.writeString(file, "some log content\n");
        assertEquals(FileFormat.PLAINTEXT, GcLogSource.detectFormat(file));
    }

    @Test
    void detectFormatDirectory() {
        assertEquals(FileFormat.DIRECTORY, GcLogSource.detectFormat(tempDir));
    }

    @Test
    void detectFormatZip() throws IOException {
        Path zipPath = createZipFile("test.zip", "test.log", "log line 1\nlog line 2\n");
        assertEquals(FileFormat.ZIP, GcLogSource.detectFormat(zipPath));
    }

    @Test
    void detectFormatGZip() throws IOException {
        Path gzPath = createGZipFile("test.gz", "log line 1\nlog line 2\n");
        assertEquals(FileFormat.GZIP, GcLogSource.detectFormat(gzPath));
    }

    @Test
    void byteCount() throws IOException {
        Path file = tempDir.resolve("size.log");
        Files.writeString(file, "hello");
        assertEquals(5, GcLogSource.byteCount(file));
    }

    @Test
    void streamPlainText() throws IOException {
        Path file = tempDir.resolve("plain.log");
        Files.writeString(file, "line1\nline2\nline3\n");
        try (Stream<String> stream = GcLogSource.streamPlainText(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(3, lines.size());
            assertEquals("line1", lines.get(0));
        }
    }

    @Test
    void streamZipEntry() throws IOException {
        Path zipPath = createZipFile("single.zip", GC_LOG, "zip line 1\nzip line 2\n");
        try (Stream<String> stream = GcLogSource.streamZipEntry(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("zip line 1", lines.get(0));
            assertEquals("zip line 2", lines.get(1));
        }
    }

    @Test
    void streamZipEntrySkipsDirectories() throws IOException {
        Path zipPath = tempDir.resolve("withdir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(ZIP_DIR_PREFIX));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(ZIP_DIR_PREFIX + GC_LOG));
            zos.write("after dir\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GcLogSource.streamZipEntry(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("after dir", lines.get(0));
        }
    }

    @Test
    void streamNamedZipEntry() throws IOException {
        Path zipPath = tempDir.resolve("named.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(GC_LOG_0));
            zos.write("segment zero\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(GC_LOG_1));
            zos.write("segment one\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GcLogSource.streamZipEntry(zipPath, GC_LOG_1)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("segment one", lines.get(0));
        }
    }

    @Test
    void streamMultiEntryZip() throws IOException {
        Path zipPath = tempDir.resolve("multi.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(GC_LOG_0));
            zos.write("first\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(GC_LOG_1));
            zos.write("second\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GcLogSource.streamMultiEntryZip(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("first", lines.get(0));
            assertEquals("second", lines.get(1));
        }
    }

    @Test
    void streamGZip() throws IOException {
        Path gzPath = createGZipFile("test.gz", "gz line 1\ngz line 2\n");
        try (Stream<String> stream = GcLogSource.streamGZip(gzPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals("gz line 1", lines.get(0));
        }
    }

    @Test
    void streamAutoDetectPlainText() throws IOException {
        Path file = tempDir.resolve("auto.log");
        Files.writeString(file, "auto line 1\nauto line 2\n");
        try (Stream<String> stream = GcLogSource.stream(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
        }
    }

    @Test
    void streamAutoDetectZip() throws IOException {
        Path zipPath = createZipFile("auto.zip", GC_LOG, "zipped\n");
        try (Stream<String> stream = GcLogSource.stream(zipPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("zipped", lines.get(0));
        }
    }

    @Test
    void streamAutoDetectGZip() throws IOException {
        Path gzPath = createGZipFile("auto.gz", "gzipped\n");
        try (Stream<String> stream = GcLogSource.stream(gzPath)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals("gzipped", lines.get(0));
        }
    }

    @Test
    void streamDirectoryThrows() {
        assertThrows(IOException.class, () -> GcLogSource.stream(tempDir));
    }

    @Test
    void discoverSources() throws IOException {
        Files.createFile(tempDir.resolve(GC_LOG_0));
        Files.createFile(tempDir.resolve(GC_LOG_1));
        Files.createFile(tempDir.resolve("other.txt"));
        try (Stream<Path> stream = GcLogSource.discoverSources(tempDir)) {
            assertEquals(3, stream.count());
        }
    }

    @Test
    void discoverSourcesWithPrefix() throws IOException {
        Files.createFile(tempDir.resolve(GC_LOG_0));
        Files.createFile(tempDir.resolve(GC_LOG_1));
        Files.createFile(tempDir.resolve("other.txt"));
        try (Stream<Path> stream = GcLogSource.discoverSources(tempDir, GC_LOG)) {
            List<Path> gcLogs = stream.collect(Collectors.toList());
            assertEquals(2, gcLogs.size());
            assertTrue(gcLogs.stream().allMatch(p -> p.getFileName().toString().startsWith(GC_LOG)));
        }
    }

    @Test
    void discoverZipEntries() throws IOException {
        Path zipPath = tempDir.resolve("entries.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(ZIP_DIR_PREFIX));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(GC_LOG_0));
            zos.write("a\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(GC_LOG_1));
            zos.write("b\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> stream = GcLogSource.discoverZipEntries(zipPath)) {
            List<String> entries = stream.collect(Collectors.toList());
            assertEquals(2, entries.size());
            assertTrue(entries.contains(GC_LOG_0));
            assertTrue(entries.contains(GC_LOG_1));
        }
    }

    private Path createZipFile(String zipName, String entryName, String content) throws IOException {
        Path zipPath = tempDir.resolve(zipName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }

    private Path createGZipFile(String gzName, String content) throws IOException {
        Path gzPath = tempDir.resolve(gzName);
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzPath.toFile()))) {
            gos.write(content.getBytes());
        }
        return gzPath;
    }
}

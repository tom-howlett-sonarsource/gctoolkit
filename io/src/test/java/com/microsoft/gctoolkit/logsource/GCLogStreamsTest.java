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

class GCLogStreamsTest {

    private static final String GC_LOG = "gc.log";
    private static final String LINE_1 = "[0.011s][info][gc] Using G1";
    private static final String LINE_2 = "[0.042s][info][gc] Heap region size: 1M";

    @TempDir
    Path tempDir;

    @Test
    void openPlainReadsLines() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n" + LINE_2 + "\n");

        try (Stream<String> stream = GCLogStreams.openPlain(plain)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void openZipReadsFirstEntry() throws IOException {
        Path zip = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry(GC_LOG));
            zos.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GCLogStreams.openZip(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void openZipSkipsDirectoryEntries() throws IOException {
        Path zip = tempDir.resolve("gc_with_dir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GCLogStreams.openZip(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void openZipThrowsOnEmptyArchive() throws IOException {
        Path zip = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            // no entries
        }

        assertThrows(IOException.class, () -> GCLogStreams.openZip(zip));
    }

    @Test
    void openGZipReadsLines() throws IOException {
        Path gzip = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzip.toFile()))) {
            gos.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes());
        }

        try (Stream<String> stream = GCLogStreams.openGZip(gzip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE_1, lines.get(0));
            assertEquals(LINE_2, lines.get(1));
        }
    }

    @Test
    void openAutoDetectsPlainText() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n");

        try (Stream<String> stream = GCLogStreams.open(plain)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void openAutoDetectsZip() throws IOException {
        Path zip = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip.toFile()))) {
            zos.putNextEntry(new ZipEntry(GC_LOG));
            zos.write((LINE_1 + "\n").getBytes());
            zos.closeEntry();
        }

        try (Stream<String> stream = GCLogStreams.open(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void openAutoDetectsGZip() throws IOException {
        Path gzip = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(gzip.toFile()))) {
            gos.write((LINE_1 + "\n").getBytes());
        }

        try (Stream<String> stream = GCLogStreams.open(gzip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }

    @Test
    void openThrowsForDirectory() {
        assertThrows(IOException.class, () -> GCLogStreams.open(tempDir));
    }

    @Test
    void openWithExplicitFormatUsesGivenFormat() throws IOException {
        Path plain = tempDir.resolve(GC_LOG);
        Files.writeString(plain, LINE_1 + "\n");

        try (Stream<String> stream = GCLogStreams.open(plain, FileFormat.PLAINTEXT)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE_1, lines.get(0));
        }
    }
}

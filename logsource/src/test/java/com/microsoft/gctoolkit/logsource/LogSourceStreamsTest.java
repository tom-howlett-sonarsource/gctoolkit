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

class LogSourceStreamsTest {

    @TempDir
    Path tempDir;

    private static final String LINE1 = "2023-01-01T00:00:00.000+0000 GC(0) Pause Young";
    private static final String LINE2 = "2023-01-01T00:00:01.000+0000 GC(1) Pause Young";

    @Test
    void plainTextLines() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.write(file, (LINE1 + "\n" + LINE2 + "\n").getBytes());

        try (Stream<String> stream = LogSourceStreams.plainTextLines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
            assertEquals(LINE2, lines.get(1));
        }
    }

    @Test
    void gzipLines() throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            out.write((LINE1 + "\n" + LINE2 + "\n").getBytes());
        }

        try (Stream<String> stream = LogSourceStreams.gzipLines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
            assertEquals(LINE2, lines.get(1));
        }
    }

    @Test
    void zipLines() throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write((LINE1 + "\n" + LINE2 + "\n").getBytes());
            out.closeEntry();
        }

        try (Stream<String> stream = LogSourceStreams.zipLines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(2, lines.size());
            assertEquals(LINE1, lines.get(0));
            assertEquals(LINE2, lines.get(1));
        }
    }

    @Test
    void zipLinesSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("gc-dir.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write((LINE1 + "\n").getBytes());
            out.closeEntry();
        }

        try (Stream<String> stream = LogSourceStreams.zipLines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE1, lines.get(0));
        }
    }

    @Test
    void linesAutoDetectsPlainText() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.write(file, (LINE1 + "\n").getBytes());

        try (Stream<String> stream = LogSourceStreams.lines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE1, lines.get(0));
        }
    }

    @Test
    void linesAutoDetectsGzip() throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            out.write((LINE1 + "\n").getBytes());
        }

        try (Stream<String> stream = LogSourceStreams.lines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE1, lines.get(0));
        }
    }

    @Test
    void linesAutoDetectsZip() throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write((LINE1 + "\n").getBytes());
            out.closeEntry();
        }

        try (Stream<String> stream = LogSourceStreams.lines(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(1, lines.size());
            assertEquals(LINE1, lines.get(0));
        }
    }

    @Test
    void linesThrowsForDirectory() {
        assertThrows(IOException.class, () -> LogSourceStreams.lines(tempDir));
    }

    @Test
    void byteSize() throws IOException {
        Path file = tempDir.resolve("gc.log");
        byte[] content = (LINE1 + "\n").getBytes();
        Files.write(file, content);

        assertEquals(content.length, LogSourceStreams.byteSize(file));
    }

    @Test
    void byteSizeForCompressedFile() throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            out.write((LINE1 + "\n").getBytes());
        }

        long size = LogSourceStreams.byteSize(file);
        assertTrue(size > 0);
    }
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceStreamsTest {

    private static final String SEGMENT_0 = "gc.log.0";
    private static final String SEGMENT_1 = "gc.log.1";

    @TempDir
    Path tempDir;

    @Test
    void plainTextLines() throws IOException {
        Path file = tempDir.resolve("gc.log");
        Files.writeString(file, "line1\nline2\nline3\n");
        try (Stream<String> lines = LogSourceStreams.plainTextLines(file)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of("line1", "line2", "line3"), result);
        }
    }

    @Test
    void zipLines() throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write("alpha\nbeta\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> lines = LogSourceStreams.zipLines(file)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of("alpha", "beta"), result);
        }
    }

    @Test
    void zipLinesSkipsDirectoryEntries() throws IOException {
        Path file = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            zos.putNextEntry(new ZipEntry("logs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("logs/gc.log"));
            zos.write("after-dir\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> lines = LogSourceStreams.zipLines(file)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of("after-dir"), result);
        }
    }

    @Test
    void gzipLines() throws IOException {
        Path file = tempDir.resolve("gc.log.gz");
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(file.toFile()))) {
            gos.write("gamma\ndelta\n".getBytes());
        }
        try (Stream<String> lines = LogSourceStreams.gzipLines(file)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of("gamma", "delta"), result);
        }
    }

    @Test
    void allZipEntryLines() throws IOException {
        Path file = tempDir.resolve("rotating.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            zos.putNextEntry(new ZipEntry(SEGMENT_0));
            zos.write("first\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(SEGMENT_1));
            zos.write("second\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> lines = LogSourceStreams.allZipEntryLines(file)) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of("first", "second"), result);
        }
    }

    @Test
    void zipEntryLines() throws IOException {
        Path file = tempDir.resolve("multi.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            zos.putNextEntry(new ZipEntry("a.log"));
            zos.write("aaa\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("b.log"));
            zos.write("bbb\n".getBytes());
            zos.closeEntry();
        }
        try (Stream<String> lines = LogSourceStreams.zipEntryLines(file, "b.log")) {
            List<String> result = lines.collect(Collectors.toList());
            assertEquals(List.of("bbb"), result);
        }
    }

    @Test
    void listZipEntries() throws IOException {
        Path file = tempDir.resolve("entries.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file.toFile()))) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(SEGMENT_0));
            zos.write("x\n".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(SEGMENT_1));
            zos.write("y\n".getBytes());
            zos.closeEntry();
        }
        List<String> entries = LogSourceStreams.listZipEntries(file);
        assertEquals(2, entries.size());
        assertTrue(entries.contains(SEGMENT_0));
        assertTrue(entries.contains(SEGMENT_1));
    }

    @Test
    void size() throws IOException {
        Path file = tempDir.resolve("sized.log");
        byte[] content = "hello world\n".getBytes();
        Files.write(file, content);
        assertEquals(content.length, LogSourceStreams.size(file));
    }
}

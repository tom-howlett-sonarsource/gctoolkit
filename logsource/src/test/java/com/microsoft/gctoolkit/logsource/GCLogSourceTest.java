// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String LINE_1 = "hello";
    private static final String LINE_2 = "world";

    @Test
    void detectsDirectory(@TempDir Path tmp) {
        assertEquals(GCLogFileFormat.DIRECTORY, GCLogSource.detect(tmp));
    }

    @Test
    void detectsPlainText(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("gc.log");
        Files.write(file, (LINE_1 + "\n" + LINE_2 + "\n").getBytes(StandardCharsets.UTF_8));
        assertEquals(GCLogFileFormat.PLAINTEXT, GCLogSource.detect(file));
        try (Stream<String> stream = GCLogSource.streamPlain(file)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void detectsAndReadsZip(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("gc.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(GCLogFileFormat.ZIP, GCLogSource.detect(zip));
        assertTrue(GCLogSource.matchesMagic(zip, GCLogSource.ZIP_MAGIC1, GCLogSource.ZIP_MAGIC2));
        try (Stream<String> stream = GCLogSource.streamZip(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void detectsAndReadsGZip(@TempDir Path tmp) throws IOException {
        Path gzipped = tmp.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gzipped))) {
            out.write((LINE_1 + "\n" + LINE_2 + "\n").getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(GCLogFileFormat.GZIP, GCLogSource.detect(gzipped));
        assertTrue(GCLogSource.matchesMagic(gzipped, GCLogSource.GZIP_MAGIC1, GCLogSource.GZIP_MAGIC2));
        try (Stream<String> stream = GCLogSource.streamGZip(gzipped)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of(LINE_1, LINE_2), lines);
        }
    }

    @Test
    void matchesMagicReturnsFalseForMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist");
        assertFalse(GCLogSource.matchesMagic(missing, 0x1F, 0x8B));
    }

    @Test
    void streamZipSkipsLeadingDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("gc-with-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write((LINE_1 + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = GCLogSource.streamZip(zip)) {
            assertEquals(List.of(LINE_1), stream.collect(Collectors.toList()));
        }
    }
}

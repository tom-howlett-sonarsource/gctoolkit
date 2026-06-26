// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileLineSourceTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");
    private static final String ZIP_FILE = "gc.zip";
    private static final String FIRST_ENTRY = "gc.log.0";
    private static final String CONTENT = "content";

    @Test
    void plainTextStreamsEveryLine(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("gc.log");
        Files.write(file, LINES);

        try (Stream<String> stream = GCLogFileLineSource.plainText(file)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void gzipStreamsEveryLine(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
        }

        try (Stream<String> stream = GCLogFileLineSource.gzip(file)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void firstZipEntryStreamsFirstFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ZIP_FILE);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(FIRST_ENTRY));
            zip.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (Stream<String> stream = GCLogFileLineSource.firstZipEntry(file)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void firstZipEntrySkipsDirectoryEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ZIP_FILE);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("logs/gc.log"));
            zip.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (Stream<String> stream = GCLogFileLineSource.firstZipEntry(file)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void firstZipEntryThrowsWhenArchiveHasNoFileEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ZIP_FILE);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
        }

        assertThrows(IOException.class, () -> GCLogFileLineSource.firstZipEntry(file));
    }

    @Test
    void zipEntryStreamsNamedEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ZIP_FILE);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(FIRST_ENTRY));
            zip.write("ignored".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("gc.log.1"));
            zip.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (Stream<String> stream = GCLogFileLineSource.zipEntry(file, "gc.log.1")) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void zipEntryReleasesFileHandleWhenStreamClosed(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ZIP_FILE);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(FIRST_ENTRY));
            zip.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        try (Stream<String> stream = GCLogFileLineSource.zipEntry(file, FIRST_ENTRY)) {
            assertEquals(List.of(CONTENT), stream.collect(Collectors.toList()));
        }

        // A leaked archive handle would block deletion on Windows, so a successful
        // delete proves the stream released its handle when it was closed.
        assertTrue(Files.deleteIfExists(file));
    }

    @Test
    void zipEntryThrowsForMissingEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(ZIP_FILE);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(FIRST_ENTRY));
            zip.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(IOException.class, () -> GCLogFileLineSource.zipEntry(file, "does-not-exist"));
    }
}

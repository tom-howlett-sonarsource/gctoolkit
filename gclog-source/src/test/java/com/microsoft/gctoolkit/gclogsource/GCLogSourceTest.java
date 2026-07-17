// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainSourceAndReportsByteSize() throws IOException {
        Path source = temporaryDirectory.resolve("gc.log");
        Files.writeString(source, "first\nsecond\n");

        assertEquals(GCLogSource.Format.PLAIN, GCLogSource.format(source));
        assertEquals(Files.size(source), GCLogSource.byteSize(source));
        try (var lines = GCLogSource.open(source)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensFirstFileInZipSource() throws IOException {
        Path source = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            writeZipEntry(zip, "logs/first.log", "first\nsecond\n");
            writeZipEntry(zip, "logs/ignored.log", "ignored\n");
        }

        assertEquals(GCLogSource.Format.ZIP, GCLogSource.format(source));
        try (var lines = GCLogSource.open(source)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void opensGzipSource() throws IOException {
        Path source = temporaryDirectory.resolve("gc.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(GCLogSource.Format.GZIP, GCLogSource.format(source));
        try (var lines = GCLogSource.open(source)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void listsAndOpensZipEntries() throws IOException {
        Path source = temporaryDirectory.resolve("rotating.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            writeZipEntry(zip, "first.log", "first\n");
            writeZipEntry(zip, "second.log", "second\n");
        }

        assertEquals(List.of("first.log", "second.log"), GCLogSource.zipEntries(source));
        try (var lines = GCLogSource.openZipEntry(source, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        try (var lines = GCLogSource.openZipEntries(source)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsDirectoriesAndMissingZipEntries() throws IOException {
        Path source = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(source))) {
        }

        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.format(temporaryDirectory));
        assertThrows(IOException.class, () -> GCLogSource.open(temporaryDirectory));
        assertThrows(IOException.class, () -> GCLogSource.open(source));
        assertThrows(IOException.class, () -> GCLogSource.openZipEntry(source, "missing.log"));
    }

    private static void writeZipEntry(
            final ZipOutputStream zip,
            final String name,
            final String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

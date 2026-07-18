// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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
    Path directory;

    @Test
    void discoversDirectoryAndPlainTextSources() throws IOException {
        Path plainText = write("gc.log", "first\nsecond\n");

        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plainText));
    }

    @Test
    void discoversAndReadsZipSourceSkippingDirectories() throws IOException {
        Path zip = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip line\n".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(List.of("zip line"), read(zip));
    }

    @Test
    void discoversAndReadsGzipSource() throws IOException {
        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip line\n".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(List.of("gzip line"), read(gzip));
    }

    @Test
    void readsPlainTextAndReportsPhysicalByteSize() throws IOException {
        Path plainText = write("gc.log", "one\ntwo\n");

        assertEquals(Files.size(plainText), GCLogSource.byteSize(plainText));
        assertEquals(List.of("one", "two"), read(plainText));
    }

    @Test
    void rejectsDirectoriesAndEmptyZipSources() throws IOException {
        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            output.finish();
        }

        assertThrows(IOException.class, () -> GCLogSource.open(directory));
        assertThrows(IOException.class, () -> GCLogSource.open(emptyZip));
    }

    @Test
    void rejectsMissingAndNullSources() {
        Path missing = directory.resolve("missing.log");

        assertThrows(IOException.class, () -> GCLogSource.discover(missing));
        assertThrows(NullPointerException.class, () -> GCLogSource.discover(null));
        assertThrows(NullPointerException.class, () -> GCLogSource.byteSize(null));
    }

    @Test
    void rejectsInvalidGzipSource() throws IOException {
        Path invalidGzip = directory.resolve("invalid.gz");
        Files.write(invalidGzip, new byte[]{0x1f, (byte) 0x8b, 0x00});

        assertThrows(IOException.class, () -> GCLogSource.open(invalidGzip));
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = directory.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = GCLogSource.open(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}

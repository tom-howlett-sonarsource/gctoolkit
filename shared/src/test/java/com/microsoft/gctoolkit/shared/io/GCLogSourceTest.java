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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String LOG_CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatsFromContent() throws IOException {
        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(writePlain()));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(writeZip()));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(writeGzip()));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
    }

    @Test
    void reportsStoredByteSize() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();

        assertEquals(Files.size(plain), GCLogSource.from(plain).sizeInBytes());
        assertEquals(Files.size(gzip), GCLogSource.sizeInBytes(gzip));
    }

    @Test
    void opensPlainZipAndGzipLines() throws IOException {
        assertLogLines(writePlain());
        assertLogLines(writeZip());
        assertLogLines(writeGzip());
    }

    @Test
    void opensPlainZipAndGzipByteStreams() throws IOException {
        assertLogBytes(writePlain());
        assertLogBytes(writeZip());
        assertLogBytes(writeGzip());
    }

    private void assertLogLines(Path path) throws IOException {
        try (var lines = GCLogSource.from(path).lines()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of("first line", "second line"), collected);
        }
    }

    private void assertLogBytes(Path path) throws IOException {
        try (var input = GCLogSource.from(path).openStream()) {
            assertArrayEquals(LOG_CONTENT.getBytes(StandardCharsets.UTF_8), input.readAllBytes());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("archive.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("compressed.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndOpensPlainText() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.log");
        Files.write(sourcePath, CONTENT);

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertEquals(sourcePath, source.path());
        assertEquals(GCLogSource.Format.PLAIN_TEXT, source.format());
        assertFalse(GCLogSource.hasMagic(sourcePath, 0x50, 0x4B));
        assertEquals(CONTENT.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    void discoversAndOpensGzip() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(sourcePath))) {
            output.write(CONTENT);
        }

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertEquals(GCLogSource.Format.GZIP, source.format());
        assertTrue(GCLogSource.hasMagic(sourcePath, 0x1F, 0x8B));
        assertEquals(CONTENT.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    void discoversAndOpensFirstZipFileEntry() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(sourcePath))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertEquals(GCLogSource.Format.ZIP, source.format());
        assertEquals(CONTENT.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    @Test
    void identifiesDirectories() throws IOException {
        GCLogSource source = GCLogSource.discover(temporaryDirectory);

        assertEquals(GCLogSource.Format.DIRECTORY, source.format());
        assertThrows(IOException.class, source::size);
        assertThrows(IOException.class, source::lines);
    }

    @Test
    void emptyZipHasNoContent() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(sourcePath))) {
        }

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertEquals(0L, source.size());
        try (var lines = source.lines()) {
            assertEquals(0L, lines.count());
        }
    }

    @Test
    void malformedGzipCannotBeOpenedOrSized() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("malformed.gz");
        Files.write(sourcePath, new byte[]{0x1F, (byte) 0x8B});
        GCLogSource source = GCLogSource.discover(sourcePath);

        assertThrows(IOException.class, source::lines);
        assertThrows(IOException.class, source::size);
        assertFalse(GCLogSource.hasMagic(temporaryDirectory.resolve("missing.log"), 0x1F, 0x8B));
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static java.util.stream.Collectors.toList;

class GCLogSourceTest {

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndReadsPlainText() throws IOException {
        Path sourcePath = Files.write(temporaryDirectory.resolve("gc.log"), CONTENT);

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, source.format());
        assertEquals(CONTENT.length, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndReadsFirstZipFileEntry() throws IOException {
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
        assertEquals(CONTENT.length, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndReadsGzip() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(sourcePath))) {
            output.write(CONTENT);
        }

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertEquals(GCLogSource.Format.GZIP, source.format());
        assertEquals(CONTENT.length, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void rejectsDirectoriesAsLineSources() throws IOException {
        GCLogSource source = GCLogSource.discover(temporaryDirectory);

        assertEquals(GCLogSource.Format.DIRECTORY, source.format());
        assertThrows(IOException.class, source::lines);
    }

    @Test
    void rejectsZipWithoutFileEntries() throws IOException {
        Path sourcePath = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(sourcePath))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        GCLogSource source = GCLogSource.discover(sourcePath);

        assertThrows(IOException.class, source::lines);
        assertThrows(IOException.class, source::byteSize);
    }
}

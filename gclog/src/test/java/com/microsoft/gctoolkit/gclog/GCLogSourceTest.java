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

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainTextAndReportsBytes() throws IOException {
        Path source = temporaryDirectory.resolve("gc.log");
        Files.writeString(source, "first\nsecond\n", StandardCharsets.UTF_8);

        GCLogSource logSource = GCLogSource.from(source);

        assertEquals(source, logSource.path());
        assertEquals(GCLogSource.Format.PLAIN_TEXT, logSource.format());
        assertEquals(Files.size(source), logSource.byteSize());
        try (var lines = logSource.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void opensFirstFileInZipArchive() throws IOException {
        Path source = temporaryDirectory.resolve("gc.zip");
        try (OutputStream output = Files.newOutputStream(source);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("directory/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("directory/gc.log"));
            zip.write("zip line\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        GCLogSource logSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.ZIP, logSource.format());
        try (var lines = logSource.lines()) {
            assertEquals(List.of("zip line"), lines.collect(toList()));
        }
    }

    @Test
    void opensGzipSource() throws IOException {
        Path source = temporaryDirectory.resolve("gc.gz");
        try (OutputStream output = Files.newOutputStream(source);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write("gzip line\n".getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource logSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.GZIP, logSource.format());
        try (var lines = logSource.lines()) {
            assertEquals(List.of("gzip line"), lines.collect(toList()));
        }
    }

    @Test
    void discoversDirectorySources() throws IOException {
        GCLogSource logSource = GCLogSource.from(temporaryDirectory);

        assertEquals(GCLogSource.Format.DIRECTORY, logSource.format());
        assertEquals(0L, logSource.byteSize());
        assertThrows(IOException.class, logSource::lines);
    }

    @Test
    void emptyZipArchiveProducesNoLines() throws IOException {
        Path source = temporaryDirectory.resolve("empty.zip");
        try (OutputStream output = Files.newOutputStream(source);
             ZipOutputStream ignored = new ZipOutputStream(output)) {
        }

        try (var lines = GCLogSource.from(source).lines()) {
            assertEquals(List.of(), lines.collect(toList()));
        }
    }

    @Test
    void missingSourceFallsBackToPlainTextAndFailsWhenOpened() {
        GCLogSource logSource = GCLogSource.from(temporaryDirectory.resolve("missing.log"));

        assertEquals(GCLogSource.Format.PLAIN_TEXT, logSource.format());
        assertThrows(IOException.class, logSource::byteSize);
        assertThrows(IOException.class, logSource::lines);
    }

    @Test
    void truncatedZipSourceProducesNoLines() throws IOException {
        Path source = temporaryDirectory.resolve("broken.zip");
        Files.write(source, new byte[]{0x50, 0x4B, 0x03});

        GCLogSource logSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.ZIP, logSource.format());
        try (var lines = logSource.lines()) {
            assertEquals(List.of(), lines.collect(toList()));
        }
    }

    @Test
    void malformedGzipSourceFailsWhenOpened() throws IOException {
        Path source = temporaryDirectory.resolve("broken.gz");
        Files.write(source, new byte[]{0x1F, (byte) 0x8B});

        GCLogSource logSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.GZIP, logSource.format());
        assertThrows(IOException.class, logSource::lines);
    }
}

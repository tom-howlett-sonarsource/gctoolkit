// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    void discoversAndReadsPlainLog() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path log = Files.write(temporaryDirectory.resolve("gc.log"), content);

        List<GCLogSource> sources = GCLogSource.discover(log);

        assertEquals(1, sources.size());
        assertEquals(GCLogSource.Format.PLAIN_TEXT, sources.get(0).format());
        assertEquals(log, sources.get(0).path());
        assertEquals("gc.log", sources.get(0).name());
        assertEquals(content.length, sources.get(0).byteSize());
        try (var input = sources.get(0).openStream()) {
            assertEquals("first\nsecond\n",
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var lines = sources.get(0).lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndReadsGzipLog() throws IOException {
        byte[] content = "gzip-first\ngzip-second\n".getBytes(StandardCharsets.UTF_8);
        Path log = temporaryDirectory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(log))) {
            output.write(content);
        }

        GCLogSource source = GCLogSource.first(log);

        assertEquals(GCLogSource.Format.GZIP, source.format());
        assertEquals(content.length, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("gzip-first", "gzip-second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndReadsEachZipLogEntry() throws IOException {
        Path log = temporaryDirectory.resolve("gc.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(log))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/first.log", "first-entry\n");
            writeEntry(output, "logs/second.log", "second-entry\n");
        }

        List<GCLogSource> sources = GCLogSource.discover(log);

        assertEquals(List.of("logs/first.log", "logs/second.log"),
                sources.stream().map(GCLogSource::name).collect(Collectors.toList()));
        assertEquals(List.of(12L, 13L), sources.stream().map(source -> {
            try {
                return source.byteSize();
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        }).collect(Collectors.toList()));
        try (var lines = sources.get(1).lines()) {
            assertEquals(List.of("second-entry"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsDirectoryAsSingleSource() throws IOException {
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.formatOf(temporaryDirectory));
        assertThrows(IOException.class, () -> GCLogSource.first(temporaryDirectory));
    }

    @Test
    void rejectsZipWithoutLogEntries() throws IOException {
        Path log = temporaryDirectory.resolve("empty.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(log))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> GCLogSource.first(log));
    }

    private static void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

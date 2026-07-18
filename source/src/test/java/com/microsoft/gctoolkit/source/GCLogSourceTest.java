// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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

    private static final byte[] CONTENT = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndOpensPlainSource() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log");
        Files.write(log, CONTENT);

        List<GCLogSource> sources = GCLogSource.discover(log);

        assertEquals(1, sources.size());
        assertEquals(GCLogSource.Format.PLAIN_TEXT, sources.get(0).format());
        assertEquals(log, sources.get(0).path());
        assertEquals(CONTENT.length, sources.get(0).size());
        try (var input = sources.get(0).openStream()) {
            assertEquals("first line\nsecond line\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var lines = sources.get(0).lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndOpensGzipSource() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(log))) {
            output.write(CONTENT);
        }

        GCLogSource source = GCLogSource.discover(log).get(0);

        assertEquals(GCLogSource.Format.GZIP, source.format());
        assertEquals(CONTENT.length, source.size());
        try (var input = source.openStream()) {
            assertEquals("first line\nsecond line\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndOpensZipEntries() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(log))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write(CONTENT);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("third line\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        List<GCLogSource> sources = GCLogSource.discover(log);

        assertEquals(List.of("logs/first.log", "second.log"),
                List.of(sources.get(0).name(), sources.get(1).name()));
        assertEquals(GCLogSource.Format.ZIP, sources.get(0).format());
        assertEquals(CONTENT.length, sources.get(0).size());
        try (var input = sources.get(0).openStream()) {
            assertEquals("first line\nsecond line\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertEquals("logs/first.log", GCLogSource.from(log).name());
        GCLogSource missingEntry = GCLogSource.fromZipEntry(log, "missing.log");
        assertThrows(IOException.class, missingEntry::openStream);
        assertThrows(NullPointerException.class, () -> GCLogSource.fromZipEntry(log, null));
    }

    @Test
    void discoversDirectorySources() throws IOException {
        Files.write(temporaryDirectory.resolve("first.log"), CONTENT);
        Files.write(temporaryDirectory.resolve("second.log"), CONTENT);
        Files.createDirectory(temporaryDirectory.resolve("nested"));

        List<GCLogSource> sources = GCLogSource.discover(temporaryDirectory);

        assertEquals(List.of("first.log", "second.log"),
                sources.stream().map(GCLogSource::name).sorted().collect(Collectors.toList()));
        assertThrows(IOException.class, () -> GCLogSource.from(temporaryDirectory));
    }

    @Test
    void rejectsZipWithoutFileEntries() throws IOException {
        Path log = temporaryDirectory.resolve("empty.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(log))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> GCLogSource.from(log));
    }
}

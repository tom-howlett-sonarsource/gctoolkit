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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsAndSizesPlainLog() throws IOException {
        Path path = writePlainLog("gc.log");

        GCLogSource source = GCLogSources.first(path);

        assertEquals(path, source.getPath());
        assertEquals("gc.log", source.getName());
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void readsAndSizesGzipLog() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource source = GCLogSources.first(path);

        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversReadsAndSizesZipEntries() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeZipEntry(output, "logs/first.log", CONTENT);
            writeZipEntry(output, "second.log", "third line\n");
        }

        List<GCLogSource> sources = GCLogSources.discover(path);

        assertEquals(List.of("logs/first.log", "second.log"),
                sources.stream().map(GCLogSource::getName).collect(Collectors.toList()));
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, sources.get(0).size());
        try (var lines = sources.get(0).lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversRegularFilesInDirectory() throws IOException {
        Path first = writePlainLog("first.log");
        Path second = writePlainLog("second.log");
        Files.createDirectory(temporaryDirectory.resolve("nested"));

        List<GCLogSource> sources = GCLogSources.discover(temporaryDirectory);

        assertEquals(List.of(first, second),
                sources.stream().map(GCLogSource::getPath).sorted().collect(Collectors.toList()));
    }

    @Test
    void rejectsEmptyZip() throws IOException {
        Path path = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(path))) {
        }

        assertThrows(IOException.class, () -> GCLogSources.first(path));
    }

    @Test
    void rejectsMissingZipEntry() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(output, "gc.log", CONTENT);
        }

        GCLogSource source = GCLogSources.zipEntry(path, "missing.log");

        assertThrows(IOException.class, source::lines);
        assertThrows(IOException.class, source::size);
    }

    private Path writePlainLog(String name) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, CONTENT);
        return path;
    }

    private static void writeZipEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

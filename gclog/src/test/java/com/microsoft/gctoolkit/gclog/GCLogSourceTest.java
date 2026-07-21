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
import java.util.Map;
import java.util.function.Function;
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
    void opensAndSizesPlainLog() throws IOException {
        Path log = write(temporaryDirectory.resolve("gc.log"), "first\nsecond\n");

        GCLogSource source = GCLogSource.first(log);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.formatOf(log));
        assertEquals(log, source.path());
        assertEquals(Files.size(log), source.sizeInBytes());
        assertEquals(List.of("first", "second"), lines(source));
    }

    @Test
    void opensAndSizesGzipLog() throws IOException {
        Path log = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(log))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource source = GCLogSource.first(log);

        assertEquals(GCLogSource.Format.GZIP, GCLogSource.formatOf(log));
        assertEquals(Files.size(log), source.sizeInBytes());
        assertEquals(List.of("first", "second"), lines(source));
    }

    @Test
    void discoversOpensAndSizesZipEntries() throws IOException {
        Path log = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(log))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            put(output, "logs/gc.log.1", "one\n");
            put(output, "logs/gc.log", "two\nthree\n");
        }

        Map<String, GCLogSource> sources = GCLogSource.discover(log).stream()
                .collect(Collectors.toMap(GCLogSource::name, Function.identity()));

        assertEquals(GCLogSource.Format.ZIP, GCLogSource.formatOf(log));
        assertEquals(2, sources.size());
        assertEquals(4L, sources.get("logs/gc.log.1").sizeInBytes());
        assertEquals(10L, sources.get("logs/gc.log").sizeInBytes());
        assertEquals(List.of("two", "three"), lines(sources.get("logs/gc.log")));
        assertEquals(List.of("one"), lines(GCLogSource.zipEntry(log, "logs/gc.log.1")));
    }

    @Test
    void discoversFilesInDirectory() throws IOException {
        write(temporaryDirectory.resolve("gc.log.1"), "one");
        write(temporaryDirectory.resolve("gc.log"), "two");
        Files.createDirectory(temporaryDirectory.resolve("ignored"));

        List<String> names = GCLogSource.discover(temporaryDirectory).stream()
                .map(GCLogSource::name)
                .sorted()
                .collect(Collectors.toList());

        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.formatOf(temporaryDirectory));
        assertEquals(List.of("gc.log", "gc.log.1"), names);
    }

    @Test
    void rejectsEmptySourceLocationsAndMissingZipEntries() throws IOException {
        Path emptyDirectory = Files.createDirectory(temporaryDirectory.resolve("empty"));
        Path zip = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(output, "gc.log", "one\n");
        }

        assertThrows(IOException.class, () -> GCLogSource.first(emptyDirectory));
        assertThrows(IOException.class, () -> GCLogSource.zipEntry(zip, "missing.log"));
    }

    @Test
    void rejectsMalformedCompressedSources() throws IOException {
        Path gzip = writeBytes(temporaryDirectory.resolve("bad.gz"), new byte[]{0x1f, (byte) 0x8b, 0});
        Path zip = writeBytes(temporaryDirectory.resolve("bad.zip"), new byte[]{0x50, 0x4b, 0});
        GCLogSource gzipSource = GCLogSource.first(gzip);

        assertThrows(IOException.class, gzipSource::lines);
        assertThrows(IOException.class, () -> GCLogSource.discover(zip));
    }

    private static List<String> lines(GCLogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private static Path write(Path path, String content) throws IOException {
        return Files.writeString(path, content);
    }

    private static Path writeBytes(Path path, byte[] content) throws IOException {
        return Files.write(path, content);
    }

    private static void put(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

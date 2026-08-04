// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainSourceSizeAndDirectoryChildren() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, CONTENT, StandardCharsets.UTF_8);

        GCLogSource source = GCLogSource.from(plain);
        assertEquals(GCLogSource.Type.PLAINTEXT, source.type());
        assertEquals(Files.size(plain), source.size());
        try (Stream<String> lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
        try (Stream<Path> files = GCLogSource.from(directory).files()) {
            assertEquals(List.of(plain), files.collect(Collectors.toList()));
        }
    }

    @Test
    void opensGzipLines() throws IOException {
        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource source = GCLogSource.from(gzip);
        assertEquals(GCLogSource.Type.GZIP, source.type());
        try (Stream<String> lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndOpensZipEntries() throws IOException {
        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        GCLogSource source = GCLogSource.from(zip);
        assertEquals(GCLogSource.Type.ZIP, source.type());
        try (Stream<String> entries = source.entries()) {
            assertEquals(List.of("logs/gc.log"), entries.collect(Collectors.toList()));
        }
        try (Stream<String> lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = source.lines("logs/gc.log")) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }
}

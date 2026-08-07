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

class GCLogSourceTest {

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "first\nsecond\n", StandardCharsets.UTF_8);

        Path gzip = directory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }

        Path zip = directory.resolve("gc.log.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertSource(plain, GCLogSource.Format.PLAIN_TEXT, List.of("first", "second"));
        assertSource(gzip, GCLogSource.Format.GZIP, List.of("gzip"));
        assertSource(zip, GCLogSource.Format.ZIP, List.of("zip"));
    }

    private void assertSource(Path path, GCLogSource.Format format, List<String> expected) throws IOException {
        GCLogSource source = GCLogSource.discover(path);
        assertEquals(format, source.getFormat());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.open()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
    }
}

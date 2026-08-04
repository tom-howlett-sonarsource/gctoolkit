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
    void discoversSizesAndStreamsSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, CONTENT, StandardCharsets.UTF_8);

        Path gzip = directory.resolve("compressed.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        Path zip = directory.resolve("archive.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertSource(plain, GCLogSource.Format.PLAIN_TEXT);
        assertSource(gzip, GCLogSource.Format.GZIP);
        assertSource(zip, GCLogSource.Format.ZIP);
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.from(directory).getFormat());
    }

    @Test
    void streamsAnEmptyZipAsAnEmptySource() throws IOException {
        Path zip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(zip))) {
            // Empty archive.
        }

        GCLogSource source = GCLogSource.from(zip);
        assertEquals(GCLogSource.Format.ZIP, source.getFormat());
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of(), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private void assertSource(Path path, GCLogSource.Format expectedFormat) throws IOException {
        GCLogSource source = GCLogSource.from(path);
        assertEquals(expectedFormat, source.getFormat());
        assertEquals(Files.size(path), source.byteSize());
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of("first line", "second line"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }
}

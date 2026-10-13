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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsSupportedSources() throws IOException {
        Path plain = directory.resolve("plain.log");
        Files.writeString(plain, CONTENT, StandardCharsets.UTF_8);
        Path gzip = gzip(CONTENT);
        Path zip = zip(CONTENT);

        assertSource(plain, GCLogSource.Format.PLAIN_TEXT);
        assertSource(gzip, GCLogSource.Format.GZIP);
        assertSource(zip, GCLogSource.Format.ZIP);
    }

    private void assertSource(Path path, GCLogSource.Format format) throws IOException {
        GCLogSource source = new GCLogSource(path);
        assertEquals(Files.size(path), source.size());
        assertEquals(format, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path gzip(String content) throws IOException {
        Path path = directory.resolve("compressed.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String content) throws IOException {
        Path path = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

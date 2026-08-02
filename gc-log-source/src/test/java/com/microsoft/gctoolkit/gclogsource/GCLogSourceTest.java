// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsPlainSource() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);

        GCLogSource source = GCLogSource.discover(path);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, source.getFormat());
        assertEquals(Files.size(path), source.byteSize());
        assertEquals(List.of("first line", "second line"), read(source));
    }

    @Test
    void discoversAndReadsGzipSource() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource source = GCLogSource.discover(path);

        assertEquals(GCLogSource.Format.GZIP, source.getFormat());
        assertEquals(Files.size(path), source.byteSize());
        assertEquals(List.of("first line", "second line"), read(source));
    }

    @Test
    void skipsDirectoriesAndReadsFirstZipFile() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        GCLogSource source = GCLogSource.discover(path);

        assertEquals(GCLogSource.Format.ZIP, source.getFormat());
        assertEquals(Files.size(path), source.byteSize());
        assertEquals(List.of("first line", "second line"), read(source));
    }

    private List<String> read(GCLogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }
}

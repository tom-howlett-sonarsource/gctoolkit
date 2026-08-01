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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(List.of("first line", "second line"), read(GCLogSource.from(plain)));
        assertEquals(List.of("first line", "second line"), read(GCLogSource.from(gzip)));
        assertEquals(List.of("first line", "second line"), read(GCLogSource.from(zip)));
    }

    @Test
    void discoversDirectoryChildrenAndZipEntries() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();

        List<GCLogSource> children = GCLogSource.discover(directory);
        assertTrue(children.stream().anyMatch(source -> source.getPath().equals(plain)));
        assertTrue(children.stream().anyMatch(source -> source.getPath().equals(zip)));

        List<GCLogSource> entries = GCLogSource.discover(zip);
        assertEquals(List.of("logs/gc.log", "second.log"), entries.stream()
                .map(GCLogSource::getName)
                .collect(Collectors.toList()));
    }

    @Test
    void reportsFileAndZipEntrySizes() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();

        assertEquals(Files.size(plain), GCLogSource.from(plain).sizeInBytes());
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length,
                GCLogSource.discover(zip).get(0).sizeInBytes());
    }

    private List<String> read(GCLogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.archive");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("another line\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

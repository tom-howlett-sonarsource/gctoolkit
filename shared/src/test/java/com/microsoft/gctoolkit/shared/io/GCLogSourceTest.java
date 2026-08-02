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

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndStreamsSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));

        assertLines(plain);
        assertLines(gzip);
        assertLines(zip);
    }

    @Test
    void reportsSourceAndZipEntrySizes() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();

        assertEquals(Files.size(plain), GCLogSource.byteSize(plain));
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length,
                GCLogSource.byteSize(zip, "gc.log"));
        assertEquals(List.of("gc.log"), GCLogSource.entries(zip));
    }

    @Test
    void streamsNamedZipEntries() throws IOException {
        Path zip = writeZip();

        try (var lines = GCLogSource.lines(zip, "gc.log")) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private void assertLines(Path path) throws IOException {
        try (var lines = GCLogSource.lines(path)) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

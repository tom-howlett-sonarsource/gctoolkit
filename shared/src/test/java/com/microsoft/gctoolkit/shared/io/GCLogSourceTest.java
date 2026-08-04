// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
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
    void reportsPhysicalSourceSize() throws IOException {
        Path source = writePlain();

        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSource.sizeInBytes(source));
    }

    @Test
    void opensSupportedSources() throws IOException {
        assertContent(writePlain());
        assertContent(writeGzip());
        assertContent(writeZip());
    }

    private void assertLines(Path source) throws IOException {
        try (var lines = GCLogSource.lines(source)) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of("first line", "second line"), collected);
        }
    }

    private void assertContent(Path source) throws IOException {
        try (InputStream input = GCLogSource.open(source)) {
            assertEquals(CONTENT, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private Path writePlain() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.writeString(source, CONTENT, StandardCharsets.UTF_8);
        return source;
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}

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

class GCLogSourceTest {

    private static final String CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndStreamsSupportedSources() throws IOException {
        assertSource(writePlain(), GCLogSource.Format.PLAIN_TEXT);
        assertSource(writeGzip(), GCLogSource.Format.GZIP);
        assertSource(writeZip(), GCLogSource.Format.ZIP);
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory).getFormat());
    }

    @Test
    void reportsPhysicalSizeAndZipEntries() throws IOException {
        Path plain = writePlain();
        GCLogSource plainSource = GCLogSource.discover(plain);
        assertEquals(Files.size(plain), plainSource.size());

        GCLogSource zipSource = GCLogSource.discover(writeZip());
        assertEquals(List.of("gc.log"), zipSource.entries());
        try (var lines = zipSource.stream("gc.log")) {
            assertEquals(List.of(CONTENT.trim()), lines.collect(Collectors.toList()));
        }
    }

    private void assertSource(Path path, GCLogSource.Format format) throws IOException {
        GCLogSource source = GCLogSource.discover(path);
        assertEquals(format, source.getFormat());
        try (var lines = source.stream()) {
            assertEquals(List.of(CONTENT.trim()), lines.collect(Collectors.toList()));
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

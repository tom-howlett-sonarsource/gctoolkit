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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {
    private static final byte[] CONTENT = "gc log\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.write(plain, CONTENT);
        assertSource(plain, GCLogSource.Format.PLAIN_TEXT);

        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT);
        }
        assertSource(gzip, GCLogSource.Format.GZIP);

        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
        }
        assertSource(zip, GCLogSource.Format.ZIP);
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
    }

    private void assertSource(Path path, GCLogSource.Format format) throws IOException {
        assertEquals(format, GCLogSource.discover(path));
        assertEquals(Files.size(path), GCLogSource.byteSize(path));
        try (var input = GCLogSource.open(path)) {
            assertEquals("gc log\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

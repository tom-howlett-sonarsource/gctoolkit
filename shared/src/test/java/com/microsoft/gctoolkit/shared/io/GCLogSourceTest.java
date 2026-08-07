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

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        assertSource(writePlain(), GCLogSource.Format.PLAIN_TEXT);
        assertSource(writeGzip(), GCLogSource.Format.GZIP);
        assertSource(writeZip(), GCLogSource.Format.ZIP);
    }

    @Test
    void discoversDirectories() {
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
    }

    private void assertSource(Path source, GCLogSource.Format format) throws IOException {
        assertEquals(format, GCLogSource.discover(source));
        assertEquals(CONTENT.length, GCLogSource.byteSize(source));
        try (var lines = GCLogSource.lines(source)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.write(path, CONTENT);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT);
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }
        return path;
    }
}

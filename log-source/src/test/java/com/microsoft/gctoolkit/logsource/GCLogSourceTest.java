// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensEverySupportedFileFormat() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertSource(plain, GCLogSource.Format.PLAIN_TEXT);
        assertSource(gzip, GCLogSource.Format.GZIP);
        assertSource(zip, GCLogSource.Format.ZIP);
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
        assertEquals(List.of("logs/gc.log"), GCLogSource.zipEntries(zip));
    }

    @Test
    void reportsTheEncodedSourceSize() throws IOException {
        Path source = writePlain();

        assertEquals(Files.size(source), GCLogSource.sizeInBytes(source));
    }

    private void assertSource(Path path, GCLogSource.Format format) throws IOException {
        assertEquals(format, GCLogSource.discover(path));
        try (var lines = GCLogSource.open(path)) {
            assertEquals(List.of("first line", "second line"), lines.collect(java.util.stream.Collectors.toList()));
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
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

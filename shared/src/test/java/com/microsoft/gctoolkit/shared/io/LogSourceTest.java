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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensPlainZipAndGzipSources() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();
        Path gzip = writeGzip();

        LogSource plainSource = LogSource.discover(plain);
        assertTrue(plainSource.isPlainText());
        assertEquals(Files.size(plain), plainSource.sizeInBytes());
        assertEquals(List.of("first line", "second line"), read(plainSource));

        LogSource zipSource = LogSource.discover(zip);
        assertTrue(zipSource.isZip());
        assertEquals(List.of("gc.log"), zipSource.entryNames());
        assertEquals(List.of("first line", "second line"), read(zipSource));

        LogSource gzipSource = LogSource.discover(gzip);
        assertTrue(gzipSource.isGZip());
        assertEquals(List.of("first line", "second line"), read(gzipSource));
    }

    private List<String> read(LogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(java.util.stream.Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
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

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

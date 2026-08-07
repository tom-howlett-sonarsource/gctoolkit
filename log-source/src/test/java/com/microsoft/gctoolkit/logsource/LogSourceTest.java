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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {

    @TempDir
    Path directory;

    @Test
    void detectsSizesAndReadsSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "plain\n", StandardCharsets.UTF_8);
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogSource.Format.PLAINTEXT, new LogSource(plain).format());
        assertEquals(LogSource.Format.GZIP, new LogSource(gzip).format());
        assertEquals(LogSource.Format.ZIP, new LogSource(zip).format());
        assertEquals(Files.size(plain), new LogSource(plain).byteSize());
        assertEquals("plain", firstLine(plain));
        assertEquals("gzip", firstLine(gzip));
        assertEquals("zip", firstLine(zip));
        assertEquals(3, LogSource.discover(directory).size());
    }

    private String firstLine(Path source) throws IOException {
        try (var lines = new LogSource(source).lines()) {
            return lines.findFirst().orElseThrow();
        }
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

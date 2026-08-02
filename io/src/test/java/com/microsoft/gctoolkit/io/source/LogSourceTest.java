// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        assertSource(writePlain(), LogSource.Format.PLAIN);
        assertSource(writeGzip(), LogSource.Format.GZIP);
        assertSource(writeZip(), LogSource.Format.ZIP);
    }

    private void assertSource(Path path, LogSource.Format expectedFormat) throws IOException {
        LogSource source = LogSource.discover(path);
        assertEquals(expectedFormat, source.format());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.lines()) {
            assertEquals("first", lines.findFirst().orElseThrow());
        }
    }

    private Path writePlain() throws IOException {
        return Files.write(directory.resolve("gc.log"), CONTENT);
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

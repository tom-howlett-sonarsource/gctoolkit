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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertSource(plain, LogSource.Format.PLAIN_TEXT);
        assertSource(gzip, LogSource.Format.GZIP);
        assertSource(zip, LogSource.Format.ZIP);
        assertEquals(LogSource.Format.DIRECTORY, new LogSource(directory).format());
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        LogSource source = new LogSource(path);
        assertEquals(format, source.format());
        assertEquals(Files.size(path), source.size());
        try (var lines = source.lines()) {
            assertEquals(CONTENT.lines().count(), lines.count());
        }
    }

    private Path writePlain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), CONTENT, StandardCharsets.UTF_8);
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
        }
        return path;
    }
}

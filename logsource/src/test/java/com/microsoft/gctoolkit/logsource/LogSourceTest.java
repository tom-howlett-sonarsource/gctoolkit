// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {

    private static final byte[] CONTENT = "log content\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.write(plain, CONTENT);

        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT);
        }

        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }

        assertSource(plain, LogSource.Format.PLAIN_TEXT);
        assertSource(gzip, LogSource.Format.GZIP);
        assertSource(zip, LogSource.Format.ZIP);
        assertEquals(Files.size(zip), LogSource.size(zip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        assertEquals(format, LogSource.discover(path));
        try (InputStream input = LogSource.open(path)) {
            assertEquals("log content\n", new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

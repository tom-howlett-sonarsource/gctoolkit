// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "plain\n", StandardCharsets.UTF_8);

        Path gzip = directory.resolve("gc.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }

        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip\n".getBytes(StandardCharsets.UTF_8));
        }

        assertSource(plain, LogSource.Format.PLAINTEXT, "plain");
        assertSource(gzip, LogSource.Format.GZIP, "gzip");
        assertSource(zip, LogSource.Format.ZIP, "zip");
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
    }

    private void assertSource(Path path, LogSource.Format format, String line) throws IOException {
        assertEquals(format, LogSource.discover(path));
        assertEquals(Files.size(path), LogSource.size(path));
        try (var lines = LogSource.open(path)) {
            assertEquals(line, lines.findFirst().orElseThrow());
        }
    }
}

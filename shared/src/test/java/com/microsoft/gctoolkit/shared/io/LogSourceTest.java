// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
        Files.writeString(plain, "plain\n");

        Path gzip = directory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes());
        }

        Path zip = directory.resolve("gc.log.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip\n".getBytes());
        }

        assertSource(plain, LogSource.Format.PLAIN_TEXT, "plain");
        assertSource(gzip, LogSource.Format.GZIP, "gzip");
        assertSource(zip, LogSource.Format.ZIP, "zip");
        assertEquals(Files.size(plain), LogSource.size(plain));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
    }

    private void assertSource(Path path, LogSource.Format format, String expected) throws IOException {
        assertEquals(format, LogSource.discover(path));
        try (var lines = LogSource.open(path)) {
            assertEquals(expected, lines.findFirst().orElseThrow());
        }
    }
}

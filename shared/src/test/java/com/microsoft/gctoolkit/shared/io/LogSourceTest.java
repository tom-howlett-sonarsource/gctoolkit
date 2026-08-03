// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "plain\n", StandardCharsets.UTF_8);
        assertSource(plain, LogSource.Format.PLAIN_TEXT, "plain");

        Path gzip = directory.resolve("gc.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }
        assertSource(gzip, LogSource.Format.GZIP, "gzip");

        Path zip = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip\n".getBytes(StandardCharsets.UTF_8));
        }
        assertSource(zip, LogSource.Format.ZIP, "zip");
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
    }

    private void assertSource(Path source, LogSource.Format format, String line) throws IOException {
        assertEquals(format, LogSource.format(source));
        assertEquals(Files.size(source), LogSource.size(source));
        try (var lines = LogSource.lines(source)) {
            assertEquals(List.of(line), lines.collect(Collectors.toList()));
        }
    }
}

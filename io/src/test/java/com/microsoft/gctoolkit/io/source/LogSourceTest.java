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
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {

    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsEverySupportedFileFormat() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, CONTENT, StandardCharsets.UTF_8);

        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        assertSource(plain, LogSource.Format.PLAIN_TEXT);
        assertSource(gzip, LogSource.Format.GZIP);
        assertSource(zip, LogSource.Format.ZIP);
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory).format());
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        LogSource source = LogSource.discover(path);
        assertEquals(format, source.format());
        assertEquals(Files.size(path), source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }
}

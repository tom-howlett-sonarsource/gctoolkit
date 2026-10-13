// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

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

class LogSourceIOTest {

    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogSourceIO.Format.PLAIN_TEXT, LogSourceIO.discover(plain));
        assertEquals(LogSourceIO.Format.GZIP, LogSourceIO.discover(gzip));
        assertEquals(LogSourceIO.Format.ZIP, LogSourceIO.discover(zip));
        assertEquals(LogSourceIO.Format.DIRECTORY, LogSourceIO.discover(directory));

        assertLines(plain);
        assertLines(gzip);
        assertLines(zip);
    }

    @Test
    void reportsPhysicalSourceSize() throws IOException {
        Path source = writePlain();
        assertEquals(Files.size(source), LogSourceIO.size(source));
    }

    private void assertLines(Path source) throws IOException {
        try (var lines = LogSourceIO.lines(source)) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), CONTENT, StandardCharsets.UTF_8);
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}

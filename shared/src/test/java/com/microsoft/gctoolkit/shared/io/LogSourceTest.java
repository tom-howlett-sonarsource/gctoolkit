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
    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        byte[] content = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);
        Path plain = directory.resolve("gc.log");
        Files.write(plain, content);

        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }

        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(content);
            output.closeEntry();
        }

        assertSource(plain, LogSource.Format.PLAIN, content.length);
        assertSource(gzip, LogSource.Format.GZIP, Files.size(gzip));
        assertSource(zip, LogSource.Format.ZIP, Files.size(zip));
    }

    private void assertSource(Path path, LogSource.Format format, long size) throws IOException {
        LogSource source = LogSource.discover(path);
        assertEquals(format, source.format());
        assertEquals(size, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
        }
    }
}

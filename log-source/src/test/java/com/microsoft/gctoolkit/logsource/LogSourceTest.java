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
    void discoversSizesAndReadsSupportedSources() throws IOException {
        byte[] content = "gc log\n".getBytes(StandardCharsets.UTF_8);
        Path plain = directory.resolve("plain");
        Files.write(plain, content);
        assertSource(plain, LogSource.Format.PLAIN, content.length);

        Path gzip = directory.resolve("gzip");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }
        assertSource(gzip, LogSource.Format.GZIP, content.length);

        Path zip = directory.resolve("zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(content);
        }
        assertSource(zip, LogSource.Format.ZIP, content.length);
    }

    private void assertSource(Path path, LogSource.Format format, long contentSize) throws IOException {
        LogSource source = LogSource.discover(path);
        assertEquals(format, source.format());
        assertEquals(Files.size(path), source.byteSize());
        try (var input = source.open()) {
            assertEquals(contentSize, input.readAllBytes().length);
        }
    }
}

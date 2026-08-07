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
import java.util.List;
import java.util.stream.Collectors;
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

        assertEquals(LogSource.Format.PLAIN_TEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(CONTENT.length, LogSource.byteSize(plain));
        assertLines(plain);
        assertLines(gzip);
        assertLines(zip);
    }

    private void assertLines(Path source) throws IOException {
        try (var lines = LogSource.open(source)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }
}

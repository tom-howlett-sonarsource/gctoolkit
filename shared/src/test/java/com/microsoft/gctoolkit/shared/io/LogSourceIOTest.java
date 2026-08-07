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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceIOTest {

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.write(plain, CONTENT);

        Path gzip = directory.resolve("gc.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
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

        assertSource(plain, LogSourceIO.Format.PLAIN_TEXT);
        assertSource(gzip, LogSourceIO.Format.GZIP);
        assertSource(zip, LogSourceIO.Format.ZIP);
        assertEquals(List.of("logs/gc.log"), LogSourceIO.discover(zip));
        try (var lines = LogSourceIO.openZipMember(zip, "logs/gc.log")) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private void assertSource(Path path, LogSourceIO.Format expectedFormat) throws IOException {
        assertEquals(expectedFormat, LogSourceIO.format(path));
        assertEquals(CONTENT.length, LogSourceIO.byteSize(path));
        try (var lines = LogSourceIO.open(path)) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }
}

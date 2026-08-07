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

class GCLogSourceTest {

    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensPlainGzipAndZipSources() throws IOException {
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
            output.closeEntry();
        }

        assertSource(plain, GCLogSource.Format.PLAIN_TEXT);
        assertSource(gzip, GCLogSource.Format.GZIP);
        assertSource(zip, GCLogSource.Format.ZIP);
        assertEquals(List.of("logs/gc.log"), GCLogSource.from(zip).entries());
    }

    @Test
    void reportsTheSourceFileByteSize() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.write(source, CONTENT.getBytes(StandardCharsets.UTF_8));

        assertEquals(Files.size(source), GCLogSource.from(source).byteSize());
    }

    private void assertSource(Path path, GCLogSource.Format format) throws IOException {
        GCLogSource source = GCLogSource.from(path);
        assertEquals(format, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }
}

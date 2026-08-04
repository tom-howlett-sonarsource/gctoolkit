// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gclogs.GCLogSource;
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

class GCLogSourceTest {

    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndSizesPlainSource() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.writeString(source, LOG_CONTENT, StandardCharsets.UTF_8);

        assertEquals(GCLogSource.Type.PLAIN_TEXT, GCLogSource.discover(source));
        assertEquals(LOG_CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSource.size(source));
        assertEquals(List.of(source), GCLogSource.discoverFiles(directory));
        assertEquals(List.of(LOG_CONTENT.trim()), read(source));
    }

    @Test
    void opensGzipSource() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(GCLogSource.Type.GZIP, GCLogSource.discover(source));
        assertEquals(List.of(LOG_CONTENT.trim()), read(source));
    }

    @Test
    void discoversEntriesAndOpensFirstFileInZipSource() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertEquals(GCLogSource.Type.ZIP, GCLogSource.discover(source));
        assertEquals(List.of("logs/gc.log"), GCLogSource.entries(source));
        assertEquals(List.of(LOG_CONTENT.trim()), read(source));
        try (var lines = GCLogSource.open(source, "logs/gc.log")) {
            assertEquals(List.of(LOG_CONTENT.trim()), lines.collect(Collectors.toList()));
        }
    }

    private List<String> read(Path source) throws IOException {
        try (var lines = GCLogSource.open(source)) {
            return lines.collect(Collectors.toList());
        }
    }
}

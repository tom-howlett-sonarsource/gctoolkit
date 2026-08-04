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

class GCLogSourceTest {

    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
        assertSource(GCLogSource.Format.PLAIN_TEXT, plain);
        assertSource(GCLogSource.Format.GZIP, gzip);
        assertSource(GCLogSource.Format.ZIP, zip);
        assertEquals(Files.size(plain), GCLogSource.byteSize(plain));
    }

    @Test
    void listsSourcesAndOpensNamedZipEntries() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();

        try (var sources = GCLogSource.list(directory)) {
            assertEquals(List.of(plain, zip).stream().sorted().collect(Collectors.toList()),
                    sources.sorted().collect(Collectors.toList()));
        }
        assertEquals(List.of("logs/gc.log"), GCLogSource.zipEntries(zip));
        try (var lines = GCLogSource.openZip(zip, "logs/gc.log")) {
            assertEquals(List.of(LOG_CONTENT.trim()), lines.collect(Collectors.toList()));
        }
    }

    private void assertSource(GCLogSource.Format expectedFormat, Path source) throws IOException {
        assertEquals(expectedFormat, GCLogSource.discover(source));
        try (var lines = GCLogSource.open(source)) {
            assertEquals(List.of(LOG_CONTENT.trim()), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

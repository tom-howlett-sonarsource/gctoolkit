// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversAndSizesSources() throws IOException {
        Path first = Files.writeString(directory.resolve("one.log"), "one\n");
        Path second = Files.writeString(directory.resolve("two.log"), "two\n");

        assertEquals(List.of(first), LogSource.discover(first));
        assertEquals(2, LogSource.discover(directory).size());
        assertEquals(Files.size(first) + Files.size(second), LogSource.size(directory));
    }

    @Test
    void opensFormatsDetectedFromContent() throws IOException {
        Path plain = Files.writeString(directory.resolve("plain.data"), "plain\n");
        Path gzip = directory.resolve("gzip.data");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }
        Path zip = directory.resolve("zip.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("zip\n".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("plain", firstLine(plain));
        assertEquals("gzip", firstLine(gzip));
        assertEquals("zip", firstLine(zip));
        assertEquals(List.of("logs/gc.log"), LogSource.discoverZipEntries(zip));
    }

    private String firstLine(Path source) throws IOException {
        try (var lines = LogSource.open(source)) {
            return lines.findFirst().orElseThrow();
        }
    }
}

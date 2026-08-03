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
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = directory.resolve("plain.log");
        Files.writeString(plain, "plain\n", StandardCharsets.UTF_8);
        Path gzip = gzip("gzip\n");
        Path zip = zip("zip\n");

        assertEquals(LogSource.Format.PLAINTEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(Files.size(plain), LogSource.size(plain));
        assertEquals(List.of("plain"), lines(plain));
        assertEquals(List.of("gzip"), lines(gzip));
        assertEquals(List.of("zip"), lines(zip));
    }

    private List<String> lines(Path path) throws IOException {
        try (var stream = LogSource.open(path)) {
            return stream.collect(java.util.stream.Collectors.toList());
        }
    }

    private Path gzip(String content) throws IOException {
        Path path = directory.resolve("source.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String content) throws IOException {
        Path path = directory.resolve("source.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

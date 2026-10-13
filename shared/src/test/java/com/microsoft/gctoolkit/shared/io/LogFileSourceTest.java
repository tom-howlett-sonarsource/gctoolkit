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

class LogFileSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        byte[] content = "one\ntwo\n".getBytes(StandardCharsets.UTF_8);
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
        }

        assertEquals(LogFileSource.Format.PLAINTEXT, LogFileSource.discover(plain));
        assertEquals(LogFileSource.Format.GZIP, LogFileSource.discover(gzip));
        assertEquals(LogFileSource.Format.ZIP, LogFileSource.discover(zip));
        assertEquals(content.length, LogFileSource.byteSize(plain));
        assertEquals(Files.size(plain) + Files.size(gzip) + Files.size(zip), LogFileSource.byteSize(directory));
        assertEquals(List.of("one", "two"), read(zip));
        assertEquals(List.of("one", "two"), read(gzip));
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = LogFileSource.open(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}

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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsSupportedSources() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path plain = directory.resolve("plain.log");
        Files.write(plain, content);
        Path gzip = writeGzip(content);
        Path zip = writeZip(content);

        assertEquals(LogSource.Kind.PLAIN_TEXT, LogSource.discover(plain));
        assertEquals(LogSource.Kind.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Kind.ZIP, LogSource.discover(zip));
        assertEquals(content.length, LogSource.byteSize(plain));
        assertEquals(List.of("first", "second"), read(zip));
        assertEquals(List.of("first", "second"), read(gzip));
    }

    private Path writeGzip(byte[] content) throws IOException {
        Path path = directory.resolve("source");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content);
        }
        return path;
    }

    private Path writeZip(byte[] content) throws IOException {
        Path path = directory.resolve("archive.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(content);
            output.closeEntry();
        }
        return path;
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = LogSource.lines(path)) {
            return lines.collect(java.util.stream.Collectors.toList());
        }
    }
}

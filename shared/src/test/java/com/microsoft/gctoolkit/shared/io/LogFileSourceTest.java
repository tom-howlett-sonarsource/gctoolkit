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
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, "first\nsecond\n", StandardCharsets.UTF_8);
        Path gzip = gzip("first\nsecond\n");
        Path zip = zip("first\nsecond\n");

        assertEquals(LogFileSource.Format.PLAIN_TEXT, LogFileSource.discover(plain));
        assertEquals(LogFileSource.Format.GZIP, LogFileSource.discover(gzip));
        assertEquals(LogFileSource.Format.ZIP, LogFileSource.discover(zip));
        assertEquals(Files.size(plain), LogFileSource.size(plain));

        assertLines(plain);
        assertLines(gzip);
        assertLines(zip);
    }

    private void assertLines(Path path) throws IOException {
        try (var lines = LogFileSource.openLines(path)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path gzip(String content) throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String content) throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

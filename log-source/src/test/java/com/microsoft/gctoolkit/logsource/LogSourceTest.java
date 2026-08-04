// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();
        Path gzip = writeGzip();

        assertSource(plain, LogSource.Format.PLAIN_TEXT, Files.size(plain));
        assertSource(zip, LogSource.Format.ZIP, Files.size(zip));
        assertSource(gzip, LogSource.Format.GZIP, Files.size(gzip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
    }

    private void assertSource(Path source, LogSource.Format format, long size) throws IOException {
        assertEquals(format, LogSource.discover(source));
        assertEquals(size, LogSource.byteSize(source));
        try (var lines = LogSource.open(source)) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.writeString(source, CONTENT, StandardCharsets.UTF_8);
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }
}

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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.stream.Collectors.toList;

class LogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertSource(plain, LogSource.Format.PLAIN_TEXT);
        assertSource(gzip, LogSource.Format.GZIP);
        assertSource(zip, LogSource.Format.ZIP);
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        LogSource source = new LogSource(path);
        assertEquals(format, source.getFormat());
        assertEquals(Files.size(path), source.size());
        assertEquals(Files.size(path), LogSource.byteSize(path));
        try (var lines = source.stream()) {
            assertEquals(List.of("first line", "second line"), lines.collect(toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

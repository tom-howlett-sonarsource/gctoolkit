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

class LogSourceTest {
    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsPlainZipAndGzipSources() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();
        Path gzip = writeGzip();

        assertSource(plain, LogSource.Format.PLAIN);
        assertSource(zip, LogSource.Format.ZIP);
        assertSource(gzip, LogSource.Format.GZIP);
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        LogSource source = LogSource.from(path);
        assertEquals(format, source.format());
        assertEquals(Files.size(path), source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), CONTENT, StandardCharsets.UTF_8);
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

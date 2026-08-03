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
    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensSupportedSources() throws IOException {
        assertSource(writePlain(), LogSource.Format.PLAIN_TEXT);
        assertSource(writeGzip(), LogSource.Format.GZIP);
        assertSource(writeZip(), LogSource.Format.ZIP);
    }

    @Test
    void reportsPhysicalSourceSize() throws IOException {
        Path source = writePlain();
        assertEquals(Files.size(source), LogSource.discover(source).byteSize());
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        LogSource source = LogSource.discover(path);
        assertEquals(format, source.format());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.write(path, CONTENT);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT);
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(CONTENT);
        }
        return path;
    }
}

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
    void discoversSizesAndReadsPlainZipAndGzipSources() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();
        Path gzip = writeGzip();

        assertSource(plain, LogFileSource.Format.PLAIN_TEXT);
        assertSource(zip, LogFileSource.Format.ZIP);
        assertSource(gzip, LogFileSource.Format.GZIP);
        assertEquals(LogFileSource.Format.DIRECTORY, LogFileSource.discover(directory).format());
    }

    private void assertSource(Path path, LogFileSource.Format expectedFormat) throws IOException {
        LogFileSource source = LogFileSource.discover(path);
        assertEquals(expectedFormat, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, "first\nsecond\n", StandardCharsets.UTF_8);
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

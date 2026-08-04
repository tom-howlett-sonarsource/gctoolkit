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

    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndStreamsSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertSource(plain, LogFileSource.Format.PLAIN_TEXT);
        assertSource(gzip, LogFileSource.Format.GZIP);
        assertSource(zip, LogFileSource.Format.ZIP);

        LogFileSource zipSource = new LogFileSource(zip);
        assertEquals(List.of("logs/gc.log"), zipSource.zipEntryNames());
        try (var lines = zipSource.lines("logs/gc.log")) {
            assertEquals(List.of(LOG_CONTENT.trim()), lines.collect(Collectors.toList()));
        }
    }

    private void assertSource(Path path, LogFileSource.Format expectedFormat) throws IOException {
        LogFileSource source = new LogFileSource(path);
        assertEquals(expectedFormat, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (var lines = source.lines()) {
            assertEquals(List.of(LOG_CONTENT.trim()), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

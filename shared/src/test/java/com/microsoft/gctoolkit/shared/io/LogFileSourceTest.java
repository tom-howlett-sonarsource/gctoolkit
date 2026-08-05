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

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndStreamsPlainZipAndGzipSources() throws IOException {
        assertSource(writePlain(), LogFileSource.Format.PLAIN_TEXT);
        assertSource(writeZip(), LogFileSource.Format.ZIP);
        assertSource(writeGzip(), LogFileSource.Format.GZIP);
    }

    @Test
    void discoversDirectories() throws IOException {
        assertEquals(LogFileSource.Format.DIRECTORY, LogFileSource.from(directory).format());
    }

    private void assertSource(Path path, LogFileSource.Format format) throws IOException {
        LogFileSource source = LogFileSource.from(path);
        assertEquals(path, source.path());
        assertEquals(format, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (var lines = source.lines()) {
            List<String> content = lines.collect(Collectors.toList());
            assertEquals(List.of("first", "second"), content);
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.write(path, CONTENT);
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT);
        }
        return path;
    }
}

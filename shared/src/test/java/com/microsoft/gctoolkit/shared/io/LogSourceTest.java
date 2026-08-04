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

class LogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndStreamsPlainZipAndGzipSources() throws IOException {
        assertSource(writePlain(), LogSource.Format.PLAIN_TEXT);
        assertSource(writeZip(), LogSource.Format.ZIP);
        assertSource(writeGzip(), LogSource.Format.GZIP);
    }

    @Test
    void reportsPhysicalByteSize() throws IOException {
        Path path = writeGzip();
        LogSource source = LogSource.discover(path);

        assertEquals(Files.size(path), source.size());
    }

    @Test
    void discoversDirectoryAndListsZipEntries() throws IOException {
        assertEquals(LogSource.Format.DIRECTORY,
                LogSource.discover(directory).getFormat());

        LogSource zip = LogSource.discover(writeZip());
        assertEquals(List.of("logs/gc.log"), zip.entries());
        try (var lines = zip.stream("logs/gc.log")) {
            assertEquals(List.of("first line", "second line"),
                    lines.collect(Collectors.toList()));
        }
    }

    private void assertSource(Path path, LogSource.Format format) throws IOException {
        LogSource source = LogSource.discover(path);
        assertEquals(format, source.getFormat());
        try (var lines = source.stream()) {
            assertEquals(List.of("first line", "second line"),
                    lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.data");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gzip.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip.data");
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

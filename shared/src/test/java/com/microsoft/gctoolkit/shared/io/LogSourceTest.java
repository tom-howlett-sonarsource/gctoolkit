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

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensSupportedFileFormats() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogSource.Format.PLAINTEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));

        assertLines(plain);
        assertLines(gzip);
        assertLines(zip);
    }

    @Test
    void reportsPhysicalFileAndDirectorySizes() throws IOException {
        Path first = directory.resolve("first.log");
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Path second = nested.resolve("second.log");
        Files.write(first, new byte[3]);
        Files.write(second, new byte[5]);

        assertEquals(3L, LogSource.size(first));
        assertEquals(8L, LogSource.size(directory));
    }

    private void assertLines(Path path) throws IOException {
        try (var lines = LogSource.open(path)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        return Files.write(directory.resolve("gc.log"), CONTENT);
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
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }
        return path;
    }
}

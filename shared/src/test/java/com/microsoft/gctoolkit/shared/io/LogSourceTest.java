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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {
    @TempDir
    Path directory;

    @Test
    void discoversAndReadsSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogSource.Format.PLAIN_TEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
        assertEquals(Files.size(plain), LogSource.size(plain));

        for (Path source : List.of(plain, gzip, zip)) {
            try (var lines = LogSource.lines(source)) {
                assertEquals("gc line", lines.findFirst().orElseThrow());
            }
        }
    }

    private Path writePlain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), "gc line\n", StandardCharsets.UTF_8);
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("gc line\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("gc line\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }
}

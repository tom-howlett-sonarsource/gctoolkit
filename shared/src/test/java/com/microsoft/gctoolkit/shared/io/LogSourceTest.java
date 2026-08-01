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

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversFormatsAndStreamsLines() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogSource.Format.PLAIN_TEXT, LogSource.format(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.format(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.format(zip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.format(directory));
        assertEquals(List.of("first", "second"), read(plain));
        assertEquals(List.of("first", "second"), read(gzip));
        assertEquals(List.of("first", "second"), read(zip));
    }

    @Test
    void reportsPhysicalSizeAndDiscoversSources() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();

        assertEquals(CONTENT.length, LogSource.size(plain));
        assertEquals(List.of("logs/gc.log"), LogSource.zipEntries(zip));
        assertEquals(List.of("first", "second"), readZipEntry(zip));
        assertEquals(2, LogSource.files(directory).size());
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = LogSource.lines(path)) {
            return lines.collect(java.util.stream.Collectors.toList());
        }
    }

    private List<String> readZipEntry(Path path) throws IOException {
        try (var lines = LogSource.zipEntryLines(path, "logs/gc.log")) {
            return lines.collect(java.util.stream.Collectors.toList());
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
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }
        return path;
    }
}

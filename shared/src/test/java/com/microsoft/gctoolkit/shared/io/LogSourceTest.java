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

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGZip();
        Path zip = writeZip();

        assertEquals(LogSource.Format.PLAIN_TEXT, LogSource.discover(plain));
        assertEquals(LogSource.Format.GZIP, LogSource.discover(gzip));
        assertEquals(LogSource.Format.ZIP, LogSource.discover(zip));
        assertEquals(LogSource.Format.DIRECTORY, LogSource.discover(directory));
        assertEquals(Files.size(plain), LogSource.byteSize(plain));

        assertEquals(List.of("plain"), read(plain));
        assertEquals(List.of("gzip"), read(gzip));
        assertEquals(List.of("zip"), read(zip));
    }

    @Test
    void opensNamedZipEntriesInRequestedOrder() throws IOException {
        Path zip = directory.resolve("entries.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeEntry(output, "one.log", "one\n");
            writeEntry(output, "two.log", "two\n");
        }

        assertEquals(List.of("one.log", "two.log"), LogSource.zipEntries(zip));
        try (var lines = LogSource.openZipEntries(zip, List.of("two.log", "one.log"))) {
            assertEquals(List.of("two", "one"), lines.collect(Collectors.toList()));
        }
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = LogSource.open(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.log");
        Files.writeString(path, "plain\n", StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGZip() throws IOException {
        Path path = directory.resolve("gzip.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/gc.log", "zip\n");
        }
        return path;
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

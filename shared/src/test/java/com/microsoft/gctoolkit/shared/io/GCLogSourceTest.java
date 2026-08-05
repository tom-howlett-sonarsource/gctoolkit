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

class GCLogSourceTest {

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));

        assertEquals(List.of("plain"), read(GCLogSource.open(plain)));
        assertEquals(List.of("gzip"), read(GCLogSource.open(gzip)));
        assertEquals(List.of("first"), read(GCLogSource.open(zip)));
    }

    @Test
    void reportsStoredByteSizeAndDiscoversDirectorySources() throws IOException {
        Path plain = writePlain();

        assertEquals(Files.size(plain), GCLogSource.byteSize(plain));
        assertEquals(List.of(plain), GCLogSource.discoverSources(directory));
    }

    @Test
    void opensNamedAndAllZipEntries() throws IOException {
        Path zip = writeZip();

        assertEquals(List.of("one.log", "two.log"), GCLogSource.zipEntries(zip));
        assertEquals(List.of("second"), read(GCLogSource.openZipEntry(zip, "two.log")));
        assertEquals(List.of("first", "second"), read(GCLogSource.openZipEntries(zip)));
    }

    private List<String> read(java.util.stream.Stream<String> lines) {
        try (java.util.stream.Stream<String> closeableLines = lines) {
            return closeableLines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path source = directory.resolve("plain.log");
        Files.writeString(source, "plain\n", StandardCharsets.UTF_8);
        return source;
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gzip.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            writeEntry(output, "one.log", "first\n");
            writeEntry(output, "two.log", "second\n");
        }
        return source;
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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

    private static final String FIRST_LINE = "first log line";
    private static final String SECOND_LINE = "second log line";

    @TempDir
    Path directory;

    @Test
    void detectsSizesAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.detectFormat(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.detectFormat(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.detectFormat(zip));
        assertEquals(Files.size(plain), GCLogSource.byteSize(plain));
        assertEquals(List.of("logs/first.log", "second.log"), GCLogSource.zipEntryNames(zip));

        assertEquals(List.of(FIRST_LINE), read(GCLogSource.open(plain)));
        assertEquals(List.of(FIRST_LINE), read(GCLogSource.open(gzip)));
        assertEquals(List.of(FIRST_LINE), read(GCLogSource.open(zip)));
        assertEquals(List.of(SECOND_LINE), read(GCLogSource.openZip(zip, "second.log")));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), read(GCLogSource.openAllZipEntries(zip)));
    }

    @Test
    void discoversDirectorySources() throws IOException {
        Path plain = writePlain();
        assertEquals(List.of(plain), GCLogSource.discover(directory));
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, FIRST_LINE + "\n", StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write((FIRST_LINE + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/first.log", FIRST_LINE);
            writeEntry(output, "second.log", SECOND_LINE);
        }
        return path;
    }

    private void writeEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write((content + "\n").getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private List<String> read(java.util.stream.Stream<String> lines) {
        try (java.util.stream.Stream<String> closeable = lines) {
            return closeable.collect(Collectors.toList());
        }
    }
}

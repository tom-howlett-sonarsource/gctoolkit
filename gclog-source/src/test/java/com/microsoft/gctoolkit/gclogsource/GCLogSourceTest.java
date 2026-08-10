// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatsAndReportsPhysicalByteSize() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(Files.size(plain), GCLogSource.size(plain));
        assertEquals(Files.size(gzip), GCLogSource.size(gzip));
        assertEquals(Files.size(zip), GCLogSource.size(zip));
    }

    @Test
    void opensPlainGzipAndFirstFileInZip() throws IOException {
        assertEquals(LOG_CONTENT, read(GCLogSource.open(writePlain())));
        assertEquals(LOG_CONTENT, read(GCLogSource.open(writeGzip())));
        assertEquals(LOG_CONTENT, read(GCLogSource.open(writeZip())));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        Path zip = writeZip();

        assertEquals(List.of("logs/gc.log"), GCLogSource.zipEntries(zip));
        try (var lines = GCLogSource.lines(zip, "logs/gc.log")) {
            assertEquals(List.of(LOG_CONTENT.trim()), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private String read(InputStream input) throws IOException {
        try (InputStream source = input) {
            return new String(source.readAllBytes(), StandardCharsets.UTF_8);
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

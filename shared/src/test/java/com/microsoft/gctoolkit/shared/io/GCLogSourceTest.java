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

    private static final String LOG_CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversSourceFormatsAndReportsPhysicalSize() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
        assertEquals(Files.size(plain), GCLogSource.sizeInBytes(plain));
    }

    @Test
    void opensPlainGzipAndFirstZipFileEntry() throws IOException {
        assertEquals(List.of(LOG_CONTENT.trim()), read(writePlain()));
        assertEquals(List.of(LOG_CONTENT.trim()), read(writeGzip()));
        assertEquals(List.of(LOG_CONTENT.trim()), read(writeZip()));
    }

    private List<String> read(Path source) throws IOException {
        try (var lines = GCLogSource.open(source)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.writeString(source, LOG_CONTENT, StandardCharsets.UTF_8);
        return source;
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}

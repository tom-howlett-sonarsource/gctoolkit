// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

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

class GCLogSourcesTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversSourceFormats() throws IOException {
        assertEquals(GCLogSources.Format.PLAIN_TEXT, GCLogSources.discover(writePlain()));
        assertEquals(GCLogSources.Format.GZIP, GCLogSources.discover(writeGzip()));
        assertEquals(GCLogSources.Format.ZIP, GCLogSources.discover(writeZip()));
        assertEquals(GCLogSources.Format.DIRECTORY, GCLogSources.discover(directory));
    }

    @Test
    void reportsPhysicalByteSize() throws IOException {
        Path source = writePlain();
        assertEquals(Files.size(source), GCLogSources.byteSize(source));
    }

    @Test
    void opensPlainGzipAndFirstZipFileEntry() throws IOException {
        assertEquals(List.of("first line", "second line"), read(writePlain()));
        assertEquals(List.of("first line", "second line"), read(writeGzip()));
        assertEquals(List.of("first line", "second line"), read(writeZip()));
    }

    private List<String> read(Path source) throws IOException {
        try (var lines = GCLogSources.open(source)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path source = directory.resolve("gc.log");
        Files.writeString(source, CONTENT, StandardCharsets.UTF_8);
        return source;
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

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

class GCLogSourceTest {

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndOpensPlainGzipAndZipSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertSource(plain, GCLogSource.Type.PLAIN_TEXT, List.of("first", "second"));
        assertSource(gzip, GCLogSource.Type.GZIP, List.of("first", "second"));
        assertSource(zip, GCLogSource.Type.ZIP, List.of("first", "second"));

        assertEquals(CONTENT.length, GCLogSource.discover(plain).contentByteSize());
        assertEquals(CONTENT.length, GCLogSource.discover(gzip).contentByteSize());
        assertEquals(CONTENT.length, GCLogSource.discover(zip).contentByteSize());
        assertEquals(Files.size(gzip), GCLogSource.discover(gzip).byteSize());
    }

    @Test
    void discoversFilesAndNamedZipEntries() throws IOException {
        Path plain = writePlain();
        Path zip = writeZip();

        GCLogSource directorySource = GCLogSource.discover(directory);
        assertEquals(GCLogSource.Type.DIRECTORY, directorySource.type());
        assertEquals(1, directorySource.files().stream().filter(plain::equals).count());

        GCLogSource zipSource = GCLogSource.discover(zip);
        assertEquals(List.of("logs/gc.log"), zipSource.entries());
        try (var lines = zipSource.open("logs/gc.log")) {
            assertEquals(List.of("first", "second"), lines.collect(java.util.stream.Collectors.toList()));
        }
    }

    private void assertSource(Path path, GCLogSource.Type type, List<String> expected) throws IOException {
        GCLogSource source = GCLogSource.discover(path);
        assertEquals(type, source.type());
        try (var lines = source.open()) {
            assertEquals(expected, lines.collect(java.util.stream.Collectors.toList()));
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

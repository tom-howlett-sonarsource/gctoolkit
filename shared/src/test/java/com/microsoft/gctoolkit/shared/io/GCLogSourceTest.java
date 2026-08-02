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

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void opensAndSizesPlainGzipAndZipSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.write(plain, CONTENT);
        Path gzip = writeGzip();
        Path zip = writeZip();

        for (Path path : List.of(plain, gzip, zip)) {
            GCLogSource source = GCLogSource.from(path);
            assertEquals(CONTENT.length, source.byteSize());
            try (var lines = source.lines()) {
                assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
            }
        }
    }

    @Test
    void discoversDirectoryAndZipEntries() throws IOException {
        Files.writeString(directory.resolve("one.log"), "one");
        Files.writeString(directory.resolve("two.log"), "two");
        try (var sources = GCLogSource.discover(directory)) {
            assertEquals(2, sources.count());
        }

        Path zip = writeZip();
        try (var sources = GCLogSource.discover(zip)) {
            assertEquals(List.of("gc.log"), sources.map(GCLogSource::name).collect(Collectors.toList()));
        }
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
            output.putNextEntry(new ZipEntry("folder/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }
        return path;
    }
}

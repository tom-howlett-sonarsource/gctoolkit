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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final String CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndReadsPlainGzipAndZipSources() throws IOException {
        assertSource(writePlain(), GCLogSource.Format.PLAIN_TEXT);
        assertSource(writeGzip(), GCLogSource.Format.GZIP);
        assertSource(writeZip(), GCLogSource.Format.ZIP);
    }

    @Test
    void opensAnEmptyZipAsAnEmptyLineStream() throws IOException {
        Path path = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(path))) {
            // Create an empty, valid ZIP container.
        }

        try (var lines = GCLogSource.discover(path).lines()) {
            assertEquals(0L, lines.count());
        }
    }

    private void assertSource(Path path, GCLogSource.Format expectedFormat) throws IOException {
        GCLogSource source = GCLogSource.discover(path);

        assertEquals(expectedFormat, source.format());
        assertEquals(Files.size(path), source.sizeInBytes());
        try (var lines = source.lines()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(CONTENT.trim()), collected);
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.data");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gzip.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

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

    private static final String LOG_CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndReadsPlainGzipAndZipSources() throws IOException {
        assertSource(writePlain(), GCLogSource.Format.PLAINTEXT);
        assertSource(writeGzip(), GCLogSource.Format.GZIP);
        assertSource(writeZip(), GCLogSource.Format.ZIP);
    }

    @Test
    void reportsStoredSourceSize() throws IOException {
        Path source = writeGzip();

        assertEquals(Files.size(source), GCLogSource.byteSize(source));
        assertEquals(Files.size(source), GCLogSource.from(source).byteSize());
    }

    @Test
    void discoversDirectories() throws IOException {
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(directory));
    }

    private void assertSource(Path path, GCLogSource.Format expectedFormat) throws IOException {
        GCLogSource source = GCLogSource.from(path);
        assertEquals(expectedFormat, source.format());
        assertEquals(path, source.path());
        try (var input = source.openStream()) {
            assertEquals(LOG_CONTENT, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
        try (var lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
        if (expectedFormat == GCLogSource.Format.ZIP) {
            assertEquals(List.of("logs/gc.log"), source.zipEntries());
            try (var lines = source.lines("logs/gc.log")) {
                assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
            }
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain-source");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gzip-source");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip-source");
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

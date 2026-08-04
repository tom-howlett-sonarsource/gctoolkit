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

class GCLogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";
    private static final byte[] CONTENT_BYTES = CONTENT.getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversAndReadsPlainSource() throws IOException {
        verify(writePlain(), GCLogSource.Format.PLAIN);
    }

    @Test
    void discoversAndReadsZipSource() throws IOException {
        Path path = writeZip();
        verify(path, GCLogSource.Format.ZIP);
        assertEquals(List.of("directory/gc.log", "second.log"), GCLogSource.from(path).entries());
        try (var lines = GCLogSource.fromZipEntry(path, "second.log").lines()) {
            assertEquals(List.of("third line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversAndReadsGzipSource() throws IOException {
        verify(writeGzip(), GCLogSource.Format.GZIP);
    }

    private void verify(Path path, GCLogSource.Format expectedFormat) throws IOException {
        GCLogSource source = GCLogSource.from(path);
        assertEquals(expectedFormat, source.getFormat());
        assertEquals(CONTENT_BYTES.length, source.size());
        try (var lines = source.lines()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.data");
        Files.write(path, CONTENT_BYTES);
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(CONTENT_BYTES);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("third line\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gzip.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT_BYTES);
        }
        return path;
    }
}

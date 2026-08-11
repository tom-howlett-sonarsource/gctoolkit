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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceTest {

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversSizesAndReadsSupportedFileSources() throws IOException {
        assertSource(writePlain(), GCLogSource.Format.PLAIN_TEXT);
        assertSource(writeGzip(), GCLogSource.Format.GZIP);
        assertSource(writeZip(), GCLogSource.Format.ZIP);
    }

    @Test
    void discoversDirectoryWithoutOpeningItsContents() throws IOException {
        Files.write(directory.resolve("one.log"), CONTENT);
        Files.write(directory.resolve("two.log"), CONTENT);

        GCLogSource source = GCLogSource.from(directory);

        assertEquals(GCLogSource.Format.DIRECTORY, source.getFormat());
        assertEquals(0L, source.getByteSize());
    }

    private void assertSource(Path path, GCLogSource.Format format) throws IOException {
        GCLogSource source = GCLogSource.from(path);
        assertEquals(format, source.getFormat());
        assertEquals(Files.size(path), source.getByteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    private Path writePlain() throws IOException {
        return Files.write(directory.resolve("plain.log"), CONTENT);
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT);
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }
        return path;
    }
}

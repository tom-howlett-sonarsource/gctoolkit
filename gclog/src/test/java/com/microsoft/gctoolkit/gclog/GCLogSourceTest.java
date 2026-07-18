// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversPlainTextByContent() throws IOException {
        Path source = write("gc.log.gz", "first\nsecond\n");

        GCLogSource gcLogSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.PLAIN_TEXT, gcLogSource.format());
        assertEquals(Files.size(source), gcLogSource.byteSize());
        assertEquals(List.of("first", "second"), lines(gcLogSource));
    }

    @Test
    void discoversAndOpensGZipContent() throws IOException {
        Path source = temporaryDirectory.resolve("gc.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        GCLogSource gcLogSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.GZIP, gcLogSource.format());
        assertEquals(Files.size(source), gcLogSource.byteSize());
        assertEquals(List.of("first", "second"), lines(gcLogSource));
    }

    @Test
    void discoversAndOpensFirstZipFile() throws IOException {
        Path source = temporaryDirectory.resolve("gc.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        GCLogSource gcLogSource = GCLogSource.from(source);

        assertEquals(GCLogSource.Format.ZIP, gcLogSource.format());
        assertEquals(Files.size(source), gcLogSource.byteSize());
        assertEquals(List.of("first", "second"), lines(gcLogSource));
    }

    @Test
    void discoversDirectoriesButDoesNotOpenThem() throws IOException {
        GCLogSource gcLogSource = GCLogSource.from(temporaryDirectory);

        assertEquals(GCLogSource.Format.DIRECTORY, gcLogSource.format());
        assertThrows(IOException.class, gcLogSource::openStream);
    }

    @Test
    void rejectsMissingSources() {
        Path source = temporaryDirectory.resolve("missing.log");

        IOException exception = assertThrows(IOException.class, () -> GCLogSource.from(source));

        assertTrue(exception.getMessage().contains(source.toString()));
    }

    private Path write(String fileName, String content) throws IOException {
        Path source = temporaryDirectory.resolve(fileName);
        Files.writeString(source, content);
        return source;
    }

    private List<String> lines(GCLogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }
}

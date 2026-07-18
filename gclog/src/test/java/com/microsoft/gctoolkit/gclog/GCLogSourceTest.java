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
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversImmediateSources() throws IOException {
        Path first = Files.writeString(temporaryDirectory.resolve("first.log"), "first");
        Path second = Files.writeString(temporaryDirectory.resolve("second.log"), "second");

        Set<Path> sources = GCLogSource.discover(temporaryDirectory).stream().collect(toSet());

        assertEquals(Set.of(first, second), sources);
    }

    @Test
    void reportsSourceSizeInBytes() throws IOException {
        Path source = Files.write(temporaryDirectory.resolve("gc.log"), new byte[]{1, 2, 3, 4});

        assertEquals(4L, GCLogSource.size(source));
    }

    @Test
    void streamsPlainText() throws IOException {
        Path source = Files.writeString(temporaryDirectory.resolve("gc.log"), "first\nsecond\n");

        try (var lines = GCLogSource.lines(source)) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void rejectsDirectoriesAsLogSources() {
        IOException exception = assertThrows(IOException.class,
                () -> GCLogSource.lines(temporaryDirectory));

        assertEquals("Unable to read " + temporaryDirectory, exception.getMessage());
    }

    @Test
    void streamsGzipText() throws IOException {
        Path source = temporaryDirectory.resolve("compressed.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        try (var lines = GCLogSource.lines(source)) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void streamsFirstZipFileEntry() throws IOException {
        Path source = temporaryDirectory.resolve("archived.log");
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

        try (var lines = GCLogSource.lines(source)) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }
}

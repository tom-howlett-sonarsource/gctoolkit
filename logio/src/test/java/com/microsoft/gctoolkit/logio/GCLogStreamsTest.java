// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogStreamsTest {

    @Test
    void plainTextReturnsLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.log");
        Files.writeString(file, "one\ntwo\nthree\n");
        try (Stream<String> lines = GCLogStreams.plainText(file)) {
            assertEquals(List.of("one", "two", "three"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void gzipReturnsDecompressedLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("some.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("alpha\nbeta\n".getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<String> lines = GCLogStreams.gzip(file)) {
            assertEquals(List.of("alpha", "beta"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipFirstEntrySkipsDirectoryEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("some.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("nested/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("nested/log.txt"));
            out.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> lines = GCLogStreams.zipFirstEntry(file)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }
}

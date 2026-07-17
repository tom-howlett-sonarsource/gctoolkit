// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

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

class SingleGCLogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipSourcesConsistently() throws IOException {
        Path plain = temporaryDirectory.resolve("gc.log");
        Files.writeString(plain, " first \n\nsecond\n");

        Path zip = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(
                Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(" first \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        Path gzip = temporaryDirectory.resolve("gc.gz");
        try (OutputStream output = new GZIPOutputStream(
                Files.newOutputStream(gzip))) {
            output.write(" first \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> expected = List.of(
                "first", "second", GCLogFile.END_OF_DATA_SENTINEL);
        assertEquals(expected, lines(plain));
        assertEquals(expected, lines(zip));
        assertEquals(expected, lines(gzip));
    }

    private static List<String> lines(final Path path) throws IOException {
        try (var lines = new SingleGCLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}

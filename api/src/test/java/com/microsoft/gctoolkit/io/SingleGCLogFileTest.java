// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingleGCLogFileTest {

    @TempDir
    Path directory;

    @Test
    void streamsPlainZipAndGzipSourcesConsistently() throws IOException {
        Path plain = Files.writeString(directory.resolve("gc.log"), " first \n\nsecond\n");
        Path zip = directory.resolve("gc.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(" first \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        Path gzip = directory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(" first \n\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> expected = List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL);
        assertEquals(expected, read(plain));
        assertEquals(expected, read(zip));
        assertEquals(expected, read(gzip));
    }

    private static List<String> read(Path path) throws IOException {
        try (var lines = new SingleGCLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

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

class SafepointLogFileTest {

    @TempDir
    Path directory;

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        Path plain = Files.writeString(directory.resolve("safepoint.log"),
                "first\nsecond\n");
        Path zip = directory.resolve("safepoint.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        Path gzip = directory.resolve("safepoint.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> expected = List.of("first", "second");
        assertEquals(expected, read(plain));
        assertEquals(expected, read(zip));
        assertEquals(expected, read(gzip));
    }

    private static List<String> read(Path path) throws IOException {
        try (var lines = new SafepointLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}

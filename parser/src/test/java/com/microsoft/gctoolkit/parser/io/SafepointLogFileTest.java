// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

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

class SafepointLogFileTest {

    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipLogs() throws IOException {
        Path plain = temporaryDirectory.resolve("safepoint.log");
        Files.writeString(plain, CONTENT);
        Path zip = temporaryDirectory.resolve("safepoint.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        Path gzip = temporaryDirectory.resolve("safepoint.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        List<String> expected = List.of("first", "second");
        assertEquals(expected, lines(plain));
        assertEquals(expected, lines(zip));
        assertEquals(expected, lines(gzip));
    }

    private static List<String> lines(Path path) throws IOException {
        try (var lines = new SafepointLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}

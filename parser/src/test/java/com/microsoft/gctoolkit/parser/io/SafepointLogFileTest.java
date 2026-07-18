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
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafepointLogFileTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void streamsPlainZipAndGZipSources() throws IOException {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path plain = Files.write(tempDirectory.resolve("safepoint.log"), content);
        Path gzip = tempDirectory.resolve("safepoint.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }
        Path zip = tempDirectory.resolve("safepoint.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write(content);
            output.closeEntry();
        }

        List<String> expected = List.of("first", "second");
        assertEquals(expected, collect(new SafepointLogFile(plain).stream()));
        assertEquals(expected, collect(new SafepointLogFile(zip).stream()));
        assertEquals(expected, collect(new SafepointLogFile(gzip).stream()));
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }
}

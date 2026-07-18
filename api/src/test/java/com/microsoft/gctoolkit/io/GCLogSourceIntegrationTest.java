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
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogSourceIntegrationTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void singleLogFilePreservesPlainZipAndGZipBehavior() throws IOException {
        byte[] content = " first \n\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path plain = Files.write(tempDirectory.resolve("gc.log"), content);
        Path gzip = tempDirectory.resolve("gc.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }
        Path zip = tempDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(content);
            output.closeEntry();
        }

        List<String> expected = List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL);
        assertEquals(expected, collect(new SingleGCLogFile(plain).stream()));
        assertEquals(expected, collect(new SingleGCLogFile(zip).stream()));
        assertEquals(expected, collect(new SingleGCLogFile(gzip).stream()));
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }
}

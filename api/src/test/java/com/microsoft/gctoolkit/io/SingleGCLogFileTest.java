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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleGCLogFileTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        Path plain = temporaryDirectory.resolve("plain.log");
        Files.writeString(plain, " first \n\nsecond\n", StandardCharsets.UTF_8);
        Path gzip = temporaryDirectory.resolve("gzip.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gzip\n".getBytes(StandardCharsets.UTF_8));
        }
        Path zip = temporaryDirectory.resolve("zip.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write("zip\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines(plain));
        assertEquals(List.of("gzip", GCLogFile.END_OF_DATA_SENTINEL), lines(gzip));
        assertEquals(List.of("zip", GCLogFile.END_OF_DATA_SENTINEL), lines(zip));

        LogFileMetadata metadata = new SingleGCLogFile(plain).getMetaData();
        assertTrue(metadata.isPlainText());
        assertEquals(Files.size(plain), metadata.getSizeInBytes());
    }

    private static List<String> lines(Path path) throws IOException {
        try (var stream = new SingleGCLogFile(path).stream()) {
            return stream.collect(toList());
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.parser.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
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
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        Path plain = Files.writeString(temporaryDirectory.resolve("safepoint.log"), "plain\n");
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(List.of("plain"), readLines(plain));
        assertEquals(List.of("gzip"), readLines(gzip));
        assertEquals(List.of("zip"), readLines(zip));
    }

    private List<String> readLines(Path source) throws IOException {
        try (var lines = new SafepointLogFile(source).stream()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writeGzip() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("gzip\n".getBytes());
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write("zip\n".getBytes());
            output.closeEntry();
        }
        return path;
    }
}

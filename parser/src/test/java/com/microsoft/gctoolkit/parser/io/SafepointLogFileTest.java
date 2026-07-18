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
    Path directory;

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        Path plain = Files.writeString(directory.resolve("safepoint.log"), "plain\n");
        Path zip = directory.resolve("safepoint.zip");
        try (OutputStream output = Files.newOutputStream(zip); ZipOutputStream archive = new ZipOutputStream(output)) {
            archive.putNextEntry(new ZipEntry("safepoint.log"));
            archive.write("zip\n".getBytes());
            archive.closeEntry();
        }
        Path gzip = directory.resolve("safepoint.gz");
        try (OutputStream output = Files.newOutputStream(gzip); GZIPOutputStream archive = new GZIPOutputStream(output)) {
            archive.write("gzip\n".getBytes());
        }

        assertEquals(List.of("plain"), lines(new SafepointLogFile(plain)));
        assertEquals(List.of("zip"), lines(new SafepointLogFile(zip)));
        assertEquals(List.of("gzip"), lines(new SafepointLogFile(gzip)));
    }

    private List<String> lines(SafepointLogFile logFile) throws IOException {
        try (var lines = logFile.stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}

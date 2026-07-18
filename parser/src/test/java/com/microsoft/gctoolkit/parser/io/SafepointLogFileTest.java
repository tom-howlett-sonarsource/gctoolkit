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

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipSources() throws IOException {
        String content = "first\nsecond\n";
        assertLogLines(Files.writeString(temporaryDirectory.resolve("safepoint.log"), content));
        assertLogLines(writeGzip(content));
        assertLogLines(writeZip(content));
    }

    private void assertLogLines(Path path) throws IOException {
        try (var lines = new SafepointLogFile(path).stream()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path writeGzip(String content) throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String content) throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/safepoint.log"));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

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

    private static final byte[] LOG = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainLog() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log");
        Files.write(path, LOG);

        assertEquals(List.of("first", "second"), lines(path));
    }

    @Test
    void streamsGzipLog() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG);
        }

        assertEquals(List.of("first", "second"), lines(path));
    }

    @Test
    void streamsFirstZipLogEntry() throws IOException {
        Path path = temporaryDirectory.resolve("safepoint.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/safepoint.log"));
            output.write(LOG);
            output.closeEntry();
        }

        assertEquals(List.of("first", "second"), lines(path));
    }

    private static List<String> lines(Path path) throws IOException {
        try (var stream = new SafepointLogFile(path).stream()) {
            return stream.collect(Collectors.toList());
        }
    }
}

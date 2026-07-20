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

class SafepointLogFileSourceTest {

    private static final byte[] LOG_CONTENT = "first line\nsecond line\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void streamsPlainZipAndGzipFiles() throws IOException {
        Path plain = temporaryDirectory.resolve("safepoint.log");
        Path gzip = temporaryDirectory.resolve("safepoint.log.gz");
        Path zip = temporaryDirectory.resolve("safepoint.log.zip");
        Files.write(plain, LOG_CONTENT);
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(LOG_CONTENT);
        }
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write(LOG_CONTENT);
            output.closeEntry();
        }

        assertEquals(expectedLines(), read(plain));
        assertEquals(expectedLines(), read(gzip));
        assertEquals(expectedLines(), read(zip));
    }

    private static List<String> read(Path path) throws IOException {
        try (Stream<String> lines = new SafepointLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }

    private static List<String> expectedLines() {
        return List.of("first line", "second line");
    }
}

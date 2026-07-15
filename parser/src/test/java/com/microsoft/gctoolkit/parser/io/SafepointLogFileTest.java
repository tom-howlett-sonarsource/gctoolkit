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

    private static final String CONTENT = "first\nsecond\n";

    @TempDir
    private Path tempDir;

    @Test
    void streamsSupportedLogSourceFormats() throws IOException {
        Path plainText = tempDir.resolve("safepoint.log");
        Files.writeString(plainText, CONTENT, StandardCharsets.UTF_8);

        Path gzip = tempDir.resolve("safepoint.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        Path zip = tempDir.resolve("safepoint.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("safepoint.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertEquals(List.of("first", "second"), lines(plainText));
        assertEquals(List.of("first", "second"), lines(gzip));
        assertEquals(List.of("first", "second"), lines(zip));
    }

    private List<String> lines(Path path) throws IOException {
        try (Stream<String> lines = new SafepointLogFile(path).stream()) {
            return lines.collect(Collectors.toList());
        }
    }
}

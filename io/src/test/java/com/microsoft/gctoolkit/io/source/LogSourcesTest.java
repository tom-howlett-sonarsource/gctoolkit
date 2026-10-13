// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourcesTest {
    @TempDir
    Path directory;

    @Test
    void discoversFormatSizeAndContents() throws Exception {
        byte[] content = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        Path plain = directory.resolve("plain.log");
        Files.write(plain, content);
        Path gzip = directory.resolve("gzip.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(content);
        }
        Path zip = directory.resolve("zip.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(content);
        }

        assertEquals(LogSourceFormat.PLAIN_TEXT, LogSources.format(plain));
        assertEquals(LogSourceFormat.GZIP, LogSources.format(gzip));
        assertEquals(LogSourceFormat.ZIP, LogSources.format(zip));
        assertEquals(content.length, LogSources.size(plain));
        assertEquals(List.of("first", "second"), read(plain));
        assertEquals(List.of("first", "second"), read(gzip));
        assertEquals(List.of("first", "second"), read(zip));
        assertEquals(List.of("logs/gc.log"), LogSources.zipEntries(zip));
    }

    private List<String> read(Path path) throws Exception {
        try (java.util.stream.Stream<String> lines = LogSources.lines(path)) {
            return lines.collect(java.util.stream.Collectors.toList());
        }
    }
}

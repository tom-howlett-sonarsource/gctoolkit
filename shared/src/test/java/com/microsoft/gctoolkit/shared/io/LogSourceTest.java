// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

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

class LogSourceTest {

    private static final byte[] FIRST_CONTENT = "first line\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_CONTENT = "second line\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void readsAndSizesPlainAndGzipSources() throws IOException {
        Path plain = directory.resolve("plain.log");
        Files.write(plain, FIRST_CONTENT);
        assertSource(LogSource.first(plain), "first line", FIRST_CONTENT.length);

        Path gzip = directory.resolve("gzip.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(FIRST_CONTENT);
        }
        assertSource(LogSource.first(gzip), "first line", FIRST_CONTENT.length);
    }

    @Test
    void discoversAndReadsEachZipEntry() throws IOException {
        Path zip = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("ignored/"));
            output.closeEntry();
            writeEntry(output, "first.log", FIRST_CONTENT);
            writeEntry(output, "second.log", SECOND_CONTENT);
        }

        List<LogSource> sources = LogSource.discover(zip);
        assertEquals(List.of("first.log", "second.log"),
                List.of(sources.get(0).name(), sources.get(1).name()));
        assertSource(sources.get(0), "first line", FIRST_CONTENT.length);
        assertSource(sources.get(1), "second line", SECOND_CONTENT.length);
    }

    private static void assertSource(LogSource source, String line, long byteSize) throws IOException {
        assertEquals(byteSize, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of(line), lines.collect(Collectors.toList()));
        }
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

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
    void opensAndSizesPlainAndGzipSources() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.write(plain, FIRST_CONTENT);
        Path gzip = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write(FIRST_CONTENT);
        }

        assertSource(plain, LogSource.Format.PLAIN, "first line");
        assertSource(gzip, LogSource.Format.GZIP, "first line");
    }

    @Test
    void discoversOpensAndSizesZipEntries() throws IOException {
        Path zip = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeEntry(output, "logs/first.log", FIRST_CONTENT);
            writeEntry(output, "logs/second.log", SECOND_CONTENT);
        }

        List<LogSource> sources = LogSource.discover(zip);
        assertEquals(List.of("logs/first.log", "logs/second.log"), sources.stream()
                .map(source -> source.entryName().orElseThrow(AssertionError::new))
                .collect(Collectors.toList()));
        assertEquals(FIRST_CONTENT.length, sources.get(0).byteSize());
        assertEquals(SECOND_CONTENT.length, sources.get(1).byteSize());
        try (var lines = sources.get(1).lines()) {
            assertEquals(List.of("second line"), lines.collect(Collectors.toList()));
        }
    }

    private void assertSource(Path path, LogSource.Format format, String expectedLine) throws IOException {
        LogSource source = LogSource.first(path);
        assertEquals(format, source.format());
        assertEquals(FIRST_CONTENT.length, source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of(expectedLine), lines.collect(Collectors.toList()));
        }
    }

    private void writeEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

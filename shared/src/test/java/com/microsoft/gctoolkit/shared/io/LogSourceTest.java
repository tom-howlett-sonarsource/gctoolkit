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

    private static final byte[] CONTENT = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensPlainText() throws IOException {
        Path path = directory.resolve("plain.log");
        Files.write(path, CONTENT);

        assertSource(path, LogSource.Format.PLAIN_TEXT);
    }

    @Test
    void discoversAndOpensGzipByContent() throws IOException {
        Path path = directory.resolve("compressed-without-extension");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT);
        }

        assertSource(path, LogSource.Format.GZIP);
    }

    @Test
    void opensFirstNonDirectoryZipEntry() throws IOException {
        Path path = directory.resolve("archive.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT);
            output.closeEntry();
        }

        assertSource(path, LogSource.Format.ZIP);
    }

    private void assertSource(Path path, LogSource.Format expectedFormat) throws IOException {
        LogSource source = LogSource.from(path);
        assertEquals(expectedFormat, source.format());
        assertEquals(Files.size(path), source.byteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }
}

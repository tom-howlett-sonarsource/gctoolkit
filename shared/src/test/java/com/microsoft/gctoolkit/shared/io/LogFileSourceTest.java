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

class LogFileSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatsFromContentAndReportsPhysicalSize() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertSource(plain, LogFileSource.Format.PLAIN_TEXT);
        assertSource(gzip, LogFileSource.Format.GZIP);
        assertSource(zip, LogFileSource.Format.ZIP);
        assertEquals(LogFileSource.Format.DIRECTORY, LogFileSource.from(directory).getFormat());
    }

    @Test
    void opensPlainGzipAndFirstRegularZipEntry() throws IOException {
        assertEquals(List.of("first line", "second line"), read(LogFileSource.from(writePlain())));
        assertEquals(List.of("first line", "second line"), read(LogFileSource.from(writeGzip())));
        assertEquals(List.of("first line", "second line"), read(LogFileSource.from(writeZip())));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        LogFileSource source = LogFileSource.from(writeZip());

        assertEquals(List.of("logs/gc.log"), source.entries());
        try (var lines = source.lines("logs/gc.log")) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private void assertSource(Path path, LogFileSource.Format format) throws IOException {
        LogFileSource source = LogFileSource.from(path);
        assertEquals(format, source.getFormat());
        assertEquals(Files.size(path), source.size());
    }

    private List<String> read(LogFileSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.data");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gzip.data");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("zip.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

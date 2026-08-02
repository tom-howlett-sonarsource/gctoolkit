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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceTest {
    private static final String FIRST_LINE = "first line";
    private static final String SECOND_LINE = "second line";

    @TempDir
    Path directory;

    @Test
    void discoversFilesAndReportsPhysicalByteSize() throws IOException {
        Path first = writePlain("first.log", FIRST_LINE);
        Path second = writePlain("second.log", SECOND_LINE);

        try (var paths = LogSource.discover(directory)) {
            assertEquals(Set.of(first, second), paths.collect(Collectors.toSet()));
        }
        assertEquals(Files.size(first), LogSource.of(first).size());
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        assertEquals(List.of(FIRST_LINE), read(LogSource.of(writePlain("plain.log", FIRST_LINE))));
        assertEquals(List.of(FIRST_LINE), read(LogSource.of(writeGzip("gzip.log.gz", FIRST_LINE))));
        assertEquals(List.of(FIRST_LINE), read(LogSource.of(writeZip())));
    }

    @Test
    void exposesZipEntriesAndCanOpenNamedEntry() throws IOException {
        LogSource source = LogSource.of(writeZip());

        assertEquals(List.of("logs/first.log", "second.log"), source.zipEntries());
        try (var lines = source.lines("second.log")) {
            assertEquals(List.of(SECOND_LINE), lines.collect(Collectors.toList()));
        }
        assertTrue(source.isZip());
    }

    private List<String> read(LogSource source) throws IOException {
        try (var lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, content + System.lineSeparator(), StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write((content + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            writeZipEntry(output, "logs/first.log", FIRST_LINE);
            writeZipEntry(output, "second.log", SECOND_LINE);
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream output, String name, String content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write((content + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}

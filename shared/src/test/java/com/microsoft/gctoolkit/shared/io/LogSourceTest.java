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

    private static final String FIRST_LINE = "first log line\n";
    private static final String SECOND_LINE = "second log line\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatsAndPhysicalByteSizes() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogSource.Format.PLAIN_TEXT, LogSource.from(plain).format());
        assertEquals(LogSource.Format.GZIP, LogSource.from(gzip).format());
        assertEquals(LogSource.Format.ZIP, LogSource.from(zip).format());
        assertEquals(LogSource.Format.DIRECTORY, LogSource.from(directory).format());
        assertEquals(Files.size(plain), LogSource.from(plain).byteSize());
        assertEquals(Files.size(gzip), LogSource.from(gzip).byteSize());
        assertEquals(Files.size(zip), LogSource.from(zip).byteSize());
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        assertEquals(List.of("first log line"), lines(writePlain()));
        assertEquals(List.of("first log line"), lines(writeGzip()));
        assertEquals(List.of("first log line"), lines(writeZip()));
    }

    @Test
    void discoversAndOpensNamedZipEntries() throws IOException {
        LogSource source = LogSource.from(writeZip());

        assertEquals(List.of("first.log", "second.log"), source.zipEntryNames());
        try (var lines = source.lines("second.log")) {
            assertEquals(List.of("second log line"), lines.collect(Collectors.toList()));
        }
    }

    private List<String> lines(Path path) throws IOException {
        try (var lines = LogSource.from(path).lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("plain.log");
        Files.writeString(path, FIRST_LINE, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("compressed.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(FIRST_LINE.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("compressed.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("first.log"));
            output.write(FIRST_LINE.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write(SECOND_LINE.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

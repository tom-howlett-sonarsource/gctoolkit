// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static java.util.stream.Collectors.toList;

class LogFileSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversAndOpensPlainText() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        Files.write(path, List.of("first", "second"), StandardCharsets.UTF_8);

        LogFileSource source = LogFileSource.from(path);

        assertEquals(LogFileFormat.PLAIN_TEXT, source.getFormat());
        assertEquals(Files.size(path), source.getByteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndOpensGzipByMagicBytes() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        LogFileSource source = LogFileSource.from(path);

        assertEquals(LogFileFormat.GZIP, source.getFormat());
        assertEquals(Files.size(path), source.getByteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversAndOpensFirstZipFileEntry() throws IOException {
        Path path = temporaryDirectory.resolve("gc.data");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        LogFileSource source = LogFileSource.from(path);

        assertEquals(LogFileFormat.ZIP, source.getFormat());
        assertEquals(Files.size(path), source.getByteSize());
        try (var lines = source.lines()) {
            assertEquals(List.of("first", "second"), lines.collect(toList()));
        }
    }

    @Test
    void discoversDirectoryAndRejectsOpeningIt() throws IOException {
        LogFileSource source = LogFileSource.from(temporaryDirectory);

        assertEquals(LogFileFormat.DIRECTORY, source.getFormat());
        assertThrows(IOException.class, source::lines);
    }

    @Test
    void opensEmptyZipAsEmptyStream() throws IOException {
        Path path = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored =
                     new ZipOutputStream(Files.newOutputStream(path))) {
        }

        try (var lines = LogFileSource.from(path).lines()) {
            assertEquals(List.of(), lines.collect(toList()));
        }
    }
}

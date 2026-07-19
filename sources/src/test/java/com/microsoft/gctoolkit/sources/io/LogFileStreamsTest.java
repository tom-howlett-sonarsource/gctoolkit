// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.sources.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileStreamsTest {

    @Test
    void openPlainReadsAllLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log");
        Files.writeString(file, "one\ntwo\nthree\n");
        try (Stream<String> stream = LogFileStreams.openPlain(file)) {
            assertEquals(List.of("one", "two", "three"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipReadsAllLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("one\ntwo\nthree\n".getBytes());
        }
        try (Stream<String> stream = LogFileStreams.openGZip(file)) {
            assertEquals(List.of("one", "two", "three"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipReadsFirstEntry(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("entry.log"));
            out.write("one\ntwo\nthree\n".getBytes());
            out.closeEntry();
        }
        try (Stream<String> stream = LogFileStreams.openZip(file)) {
            assertEquals(List.of("one", "two", "three"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsLeadingDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/entry.log"));
            out.write("only line\n".getBytes());
            out.closeEntry();
        }
        try (Stream<String> stream = LogFileStreams.openZip(file)) {
            assertEquals(List.of("only line"), stream.collect(Collectors.toList()));
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogStreamsTest {

    @Test
    void openPlainReadsAllLines(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log");
        Files.writeString(file, "one\ntwo\nthree\n");
        try (Stream<String> stream = LogStreams.openPlain(file)) {
            assertEquals(List.of("one", "two", "three"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipReadsFirstEntry(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("data.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("segment.log"));
            out.write("alpha\nbeta\n".getBytes());
            out.closeEntry();
        }
        try (Stream<String> stream = LogStreams.openZip(zip)) {
            assertEquals(List.of("alpha", "beta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsLeadingDirectoryEntries(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("with-dir.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            ZipEntry dir = new ZipEntry("dir/");
            out.putNextEntry(dir);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/inner.log"));
            out.write("gamma\n".getBytes());
            out.closeEntry();
        }
        try (Stream<String> stream = LogStreams.openZip(zip)) {
            assertEquals(List.of("gamma"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipReadsAllLines(@TempDir Path tempDir) throws IOException {
        Path gz = tempDir.resolve("data.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("first\nsecond\n".getBytes());
        }
        try (Stream<String> stream = LogStreams.openGZip(gz)) {
            assertEquals(List.of("first", "second"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openDispatchesByFormat(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("dispatch.log");
        Files.writeString(plain, "line\n");
        try (Stream<String> stream = LogStreams.open(plain, LogStreamFormat.PLAINTEXT)) {
            assertEquals(List.of("line"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openThrowsForUnknownFormat(@TempDir Path tempDir) {
        Path plain = tempDir.resolve("noop");
        assertThrows(IOException.class, () -> LogStreams.open(plain, LogStreamFormat.UNKNOWN));
        assertThrows(IOException.class, () -> LogStreams.open(plain, LogStreamFormat.DIRECTORY));
    }
}

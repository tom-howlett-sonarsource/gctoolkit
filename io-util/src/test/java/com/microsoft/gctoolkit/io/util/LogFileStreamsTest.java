// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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

class LogFileStreamsTest {

    @Test
    void openPlainStreamsAllLines(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.txt");
        Files.writeString(file, "one\ntwo\nthree\n");
        try (Stream<String> lines = LogFileStreams.openPlain(file)) {
            assertEquals(List.of("one", "two", "three"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipStreamsAllLines(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.gz");
        try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.write("alpha\nbeta\n".getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<String> lines = LogFileStreams.openGZip(file)) {
            assertEquals(List.of("alpha", "beta"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipStreamsFirstEntry(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("plain.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            zos.putNextEntry(new ZipEntry("first.log"));
            zos.write("uno\ndos\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("second.log"));
            zos.write("nope\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        try (Stream<String> lines = LogFileStreams.openZip(file)) {
            assertEquals(List.of("uno", "dos"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsLeadingDirectoryEntries(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("dir.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            zos.putNextEntry(new ZipEntry("dir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("dir/inside.log"));
            zos.write("in-a-dir\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        try (Stream<String> lines = LogFileStreams.openZip(file)) {
            assertEquals(List.of("in-a-dir"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openDispatchesByFormat(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("dispatch.txt");
        Files.writeString(plain, "line\n");
        try (Stream<String> lines = LogFileStreams.open(plain, LogFileFormat.PLAINTEXT)) {
            assertEquals(List.of("line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openThrowsForUnsupportedFormat(@TempDir Path tmp) {
        Path plain = tmp.resolve("dir");
        assertThrows(IOException.class,
                () -> LogFileStreams.open(plain, LogFileFormat.DIRECTORY));
        assertThrows(IOException.class,
                () -> LogFileStreams.open(plain, LogFileFormat.UNKNOWN));
    }
}

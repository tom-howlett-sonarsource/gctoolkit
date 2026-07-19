// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
    void opensPlainTextFile(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("plain.log"), "one\ntwo\nthree\n");
        try (Stream<String> stream = LogFileStreams.openPlainText(file)) {
            assertEquals(List.of("one", "two", "three"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void opensGZipArchive(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("alpha\nbeta\n".getBytes());
        }
        try (Stream<String> stream = LogFileStreams.openGZip(file)) {
            assertEquals(List.of("alpha", "beta"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void opensZipArchiveFirstEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("subdir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("first\nsecond\n".getBytes());
            out.closeEntry();
        }
        try (Stream<String> stream = LogFileStreams.openZip(file)) {
            assertEquals(List.of("first", "second"), stream.collect(Collectors.toList()));
        }
    }
}

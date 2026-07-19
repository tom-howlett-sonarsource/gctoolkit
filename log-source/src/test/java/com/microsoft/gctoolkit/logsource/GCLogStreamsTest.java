// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class GCLogStreamsTest {

    @Test
    void openPlainReadsAllLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.log");
        Files.write(file, List.of("alpha", "beta", "gamma"));
        try (Stream<String> stream = GCLogStreams.openPlain(file)) {
            assertEquals(List.of("alpha", "beta", "gamma"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipReadsFirstEntry(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("first.log"));
            out.write("one\ntwo\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("second.log"));
            out.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = GCLogStreams.openZip(file)) {
            assertEquals(List.of("one", "two"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipSkipsDirectoryEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dir-first.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("folder/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("folder/log.txt"));
            out.write("body\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        try (Stream<String> stream = GCLogStreams.openZip(file)) {
            assertEquals(List.of("body"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipReadsAllLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("compressed\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<String> stream = GCLogStreams.openGZip(file)) {
            assertEquals(List.of("compressed", "second"), stream.collect(Collectors.toList()));
        }
    }
}

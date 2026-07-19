// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogStreamsTest {

    private static Path writePlain(Path dir) throws IOException {
        Path plain = dir.resolve("gc.log");
        Files.writeString(plain, "one\ntwo\nthree\n");
        return plain;
    }

    private static Path writeGZip(Path dir) throws IOException {
        Path gz = dir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("gz-one\ngz-two\n".getBytes(StandardCharsets.UTF_8));
        }
        return gz;
    }

    private static Path writeZipWithLeadingDirectory(Path dir) throws IOException {
        Path zip = dir.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("nested/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("zip-one\nzip-two\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    @Test
    void openPlainReadsAllLines(@TempDir Path dir) throws IOException {
        try (Stream<String> lines = GCLogStreams.openPlain(writePlain(dir))) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of("one", "two", "three"), collected);
        }
    }

    @Test
    void openGZipDecodesLines(@TempDir Path dir) throws IOException {
        try (Stream<String> lines = GCLogStreams.openGZip(writeGZip(dir))) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of("gz-one", "gz-two"), collected);
        }
    }

    @Test
    void openZipSkipsLeadingDirectoryEntries(@TempDir Path dir) throws IOException {
        try (Stream<String> lines = GCLogStreams.openZip(writeZipWithLeadingDirectory(dir))) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of("zip-one", "zip-two"), collected);
        }
    }

    @Test
    void openDispatchesOnFormat(@TempDir Path dir) throws IOException {
        Path plain = writePlain(dir);
        try (Stream<String> lines = GCLogStreams.open(plain, GCLogSourceFormat.PLAINTEXT)) {
            assertTrue(lines.anyMatch("two"::equals));
        }
        try (Stream<String> lines = GCLogStreams.open(writeGZip(dir), GCLogSourceFormat.GZIP)) {
            assertTrue(lines.anyMatch("gz-one"::equals));
        }
        try (Stream<String> lines = GCLogStreams.open(writeZipWithLeadingDirectory(dir), GCLogSourceFormat.ZIP)) {
            assertTrue(lines.anyMatch("zip-two"::equals));
        }
    }

    @Test
    void openRejectsUnreadableFormats(@TempDir Path dir) throws IOException {
        Path plain = writePlain(dir);
        assertThrows(IOException.class, () -> GCLogStreams.open(plain, GCLogSourceFormat.UNKNOWN));
        assertThrows(IOException.class, () -> GCLogStreams.open(plain, GCLogSourceFormat.DIRECTORY));
    }
}

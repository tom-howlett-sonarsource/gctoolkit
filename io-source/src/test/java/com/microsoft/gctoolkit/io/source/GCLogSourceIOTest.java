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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceIOTest {

    @Test
    void detectPlainText(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("plain.log");
        Files.writeString(plain, "hello world\n");
        assertEquals(GCLogFormat.PLAINTEXT, GCLogSourceIO.detectFormat(plain));
    }

    @Test
    void detectDirectory(@TempDir Path dir) {
        assertEquals(GCLogFormat.DIRECTORY, GCLogSourceIO.detectFormat(dir));
    }

    @Test
    void detectGZip(@TempDir Path dir) throws IOException {
        Path gz = dir.resolve("log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("gz line\n".getBytes());
        }
        assertEquals(GCLogFormat.GZIP, GCLogSourceIO.detectFormat(gz));
    }

    @Test
    void detectZip(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("entry.log"));
            out.write("zip line\n".getBytes());
            out.closeEntry();
        }
        assertEquals(GCLogFormat.ZIP, GCLogSourceIO.detectFormat(zip));
    }

    @Test
    void hasMagicMatches(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("e"));
            out.write("x".getBytes());
            out.closeEntry();
        }
        assertTrue(GCLogSourceIO.hasMagic(zip, GCLogSourceIO.ZIP_MAGIC1, GCLogSourceIO.ZIP_MAGIC2));
        assertFalse(GCLogSourceIO.hasMagic(zip, GCLogSourceIO.GZIP_MAGIC1, GCLogSourceIO.GZIP_MAGIC2));
    }

    @Test
    void hasMagicReturnsFalseForMissingFile(@TempDir Path dir) {
        Path missing = dir.resolve("nope");
        assertFalse(GCLogSourceIO.hasMagic(missing, GCLogSourceIO.ZIP_MAGIC1, GCLogSourceIO.ZIP_MAGIC2));
    }

    @Test
    void openPlainStreamReadsLines(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("plain.log");
        Files.writeString(plain, "a\nb\nc\n");
        try (Stream<String> stream = GCLogSourceIO.openPlainStream(plain)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("a", "b", "c"), lines);
        }
    }

    @Test
    void openZipStreamSkipsDirectoryEntries(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/inner.log"));
            out.write("inner line\n".getBytes());
            out.closeEntry();
        }
        try (Stream<String> stream = GCLogSourceIO.openZipStream(zip)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("inner line"), lines);
        }
    }

    @Test
    void openGZipStreamReadsDecompressedLines(@TempDir Path dir) throws IOException {
        Path gz = dir.resolve("log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("line1\nline2\n".getBytes());
        }
        try (Stream<String> stream = GCLogSourceIO.openGZipStream(gz)) {
            List<String> lines = stream.collect(Collectors.toList());
            assertEquals(List.of("line1", "line2"), lines);
        }
    }
}

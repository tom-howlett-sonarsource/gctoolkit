// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    @Test
    void detectPlainFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.txt");
        Files.writeString(file, "line one\nline two\n");
        assertEquals(GCLogSources.Format.PLAINTEXT, GCLogSources.detect(file));
        assertEquals(Files.size(file), GCLogSources.byteSize(file));
    }

    @Test
    void detectDirectory(@TempDir Path dir) {
        assertEquals(GCLogSources.Format.DIRECTORY, GCLogSources.detect(dir));
    }

    @Test
    void detectAndOpenGzip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.gz");
        try (OutputStream raw = Files.newOutputStream(file);
             GZIPOutputStream gz = new GZIPOutputStream(raw)) {
            gz.write("alpha\nbeta\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(GCLogSources.Format.GZIP, GCLogSources.detect(file));
        try (Stream<String> lines = GCLogSources.openGzip(file)) {
            assertEquals(List.of("alpha", "beta"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void detectAndOpenZip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.zip");
        try (OutputStream raw = Files.newOutputStream(file);
             ZipOutputStream zos = new ZipOutputStream(raw)) {
            ZipEntry directoryEntry = new ZipEntry("meta/");
            zos.putNextEntry(directoryEntry);
            zos.closeEntry();
            ZipEntry entry = new ZipEntry("gc.log");
            zos.putNextEntry(entry);
            zos.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertEquals(GCLogSources.Format.ZIP, GCLogSources.detect(file));
        try (Stream<String> lines = GCLogSources.openZip(file)) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void openPlainReturnsLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log.txt");
        Files.writeString(file, "one\ntwo\nthree\n");
        try (Stream<String> lines = GCLogSources.openPlain(file)) {
            assertEquals(List.of("one", "two", "three"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void listDirectoryEnumeratesChildren(@TempDir Path dir) throws IOException {
        Path a = Files.createFile(dir.resolve("a.log"));
        Path b = Files.createFile(dir.resolve("b.log"));
        try (Stream<Path> children = GCLogSources.listDirectory(dir)) {
            List<String> names = children.map(p -> p.getFileName().toString())
                    .sorted()
                    .collect(Collectors.toList());
            assertEquals(List.of("a.log", "b.log"), names);
        }
        assertTrue(Files.exists(a));
        assertTrue(Files.exists(b));
    }

    @Test
    void hasMagicMatchesLeadingBytes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("marker.bin");
        Files.write(file, new byte[]{(byte) 0xCA, (byte) 0xFE, 0x00, 0x01});
        assertTrue(GCLogSources.hasMagic(file, 0xCA, 0xFE));
        assertTrue(!GCLogSources.hasMagic(file, 0xDE, 0xAD));
    }

    @Test
    void detectUnknownForMissingPath(@TempDir Path dir) {
        assertEquals(GCLogSources.Format.UNKNOWN, GCLogSources.detect(dir.resolve("does-not-exist")));
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final List<String> LINES = List.of("alpha", "beta", "gamma");

    @Test
    void detectsPlainTextFile(@TempDir Path dir) throws IOException {
        Path file = writePlainText(dir.resolve("plain.log"));
        assertEquals(SourceFormat.PLAINTEXT, GCLogSource.detectFormat(file));
    }

    @Test
    void detectsDirectory(@TempDir Path dir) {
        assertEquals(SourceFormat.DIRECTORY, GCLogSource.detectFormat(dir));
    }

    @Test
    void detectsZipFile(@TempDir Path dir) throws IOException {
        Path zip = writeZip(dir.resolve("log.zip"));
        assertEquals(SourceFormat.ZIP, GCLogSource.detectFormat(zip));
    }

    @Test
    void detectsGZipFile(@TempDir Path dir) throws IOException {
        Path gz = writeGZip(dir.resolve("log.gz"));
        assertEquals(SourceFormat.GZIP, GCLogSource.detectFormat(gz));
    }

    @Test
    void sizeInBytesMatchesFile(@TempDir Path dir) throws IOException {
        Path file = writePlainText(dir.resolve("size.log"));
        assertEquals(Files.size(file), GCLogSource.sizeInBytes(file));
    }

    @Test
    void openPlainTextStreamsAllLines(@TempDir Path dir) throws IOException {
        Path file = writePlainText(dir.resolve("plain.log"));
        try (Stream<String> stream = GCLogSource.openPlainText(file)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openZipStreamsFirstEntry(@TempDir Path dir) throws IOException {
        Path zip = writeZip(dir.resolve("log.zip"));
        try (Stream<String> stream = GCLogSource.openZip(zip)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openGZipStreamsAllLines(@TempDir Path dir) throws IOException {
        Path gz = writeGZip(dir.resolve("log.gz"));
        try (Stream<String> stream = GCLogSource.openGZip(gz)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void openDispatchesByFormat(@TempDir Path dir) throws IOException {
        Path plain = writePlainText(dir.resolve("p.log"));
        Path zip = writeZip(dir.resolve("z.zip"));
        Path gz = writeGZip(dir.resolve("g.gz"));

        for (Path p : List.of(plain, zip, gz)) {
            try (Stream<String> stream = GCLogSource.open(p, GCLogSource.detectFormat(p))) {
                assertEquals(LINES, stream.collect(Collectors.toList()));
            }
        }
    }

    @Test
    void openRejectsDirectoryFormat(@TempDir Path dir) {
        IOException ioe = assertThrows(IOException.class,
                () -> GCLogSource.open(dir, SourceFormat.DIRECTORY));
        assertTrue(ioe.getMessage().contains(dir.toString()));
    }

    @Test
    void openRejectsUnknownFormat(@TempDir Path dir) {
        assertThrows(IOException.class,
                () -> GCLogSource.open(dir.resolve("missing"), SourceFormat.UNKNOWN));
    }

    private static Path writePlainText(Path path) throws IOException {
        Files.write(path, LINES);
        return path;
    }

    private static Path writeZip(Path path) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
            zos.putNextEntry(new ZipEntry("entry.log"));
            zos.write(String.join("\n", LINES).getBytes());
            zos.closeEntry();
        }
        return path;
    }

    private static Path writeGZip(Path path) throws IOException {
        try (GZIPOutputStream gos = new GZIPOutputStream(Files.newOutputStream(path))) {
            gos.write(String.join("\n", LINES).getBytes());
        }
        return path;
    }
}

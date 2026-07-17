// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversSourceFormatFromContent() throws IOException {
        Path plain = writePlain("plain.data", "plain");
        Path gzip = writeGzip("gzip.data", "gzip");
        Path zip = writeZip("zip.data", "gc.log", "zip");

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.discover(zip));
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.discover(temporaryDirectory));
    }

    @Test
    void reportsPhysicalSourceSize() throws IOException {
        Path source = writePlain("gc.log", "one\ntwo\n");

        assertEquals(Files.size(source), GCLogSource.byteSize(source));
    }

    @Test
    void opensPlainGzipAndFirstZipFile() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("gzip.log", "three\nfour\n");
        Path zip = writeZipWithDirectory("zip.log");

        assertEquals(List.of("one", "two"), readLines(plain));
        assertEquals(List.of("three", "four"), readLines(gzip));
        assertEquals(List.of("five", "six"), readLines(zip));
    }

    @Test
    void opensEmptyZipAsEmptyStream() throws IOException {
        Path zip = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(zip))) {
            // Empty archive.
        }

        assertEquals(List.of(), readLines(zip));
    }

    @Test
    void rejectsDirectoryAsLineSource() {
        assertThrows(IOException.class, () -> GCLogSource.openLines(temporaryDirectory));
    }

    private List<String> readLines(Path source) throws IOException {
        try (var lines = GCLogSource.openLines(source)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private Path writeGzip(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes());
        }
        return path;
    }

    private Path writeZip(String name, String entryName, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes());
            output.closeEntry();
        }
        return path;
    }

    private Path writeZipWithDirectory(String name) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("five\nsix\n".getBytes());
            output.closeEntry();
            output.putNextEntry(new ZipEntry("ignored.log"));
            output.write("ignored\n".getBytes());
            output.closeEntry();
        }
        return path;
    }
}

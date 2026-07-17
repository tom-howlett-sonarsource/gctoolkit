// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    void discoversSourceTypesByContent() throws IOException {
        Path plain = writePlain("plain.data", "plain");
        Path gzip = writeGzip("gzip.data", "gzip");
        Path zip = writeZip("zip.data", "log.txt", "zip");

        assertEquals(GCLogSource.Type.DIRECTORY, GCLogSource.discover(temporaryDirectory));
        assertEquals(GCLogSource.Type.PLAIN_TEXT, GCLogSource.discover(plain));
        assertEquals(GCLogSource.Type.GZIP, GCLogSource.discover(gzip));
        assertEquals(GCLogSource.Type.ZIP, GCLogSource.discover(zip));
    }

    @Test
    void reportsPhysicalSourceByteSize() throws IOException {
        Path source = writePlain("gc.log", "one\ntwo\n");

        assertEquals(Files.size(source), GCLogSource.byteSize(source));
    }

    @Test
    void opensPlainGzipAndFirstZipFileAsLines() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("gzip.log", "one\ntwo\n");
        Path zip = writeZip("zip.log", "logs/gc.log", "one\ntwo\n");

        assertEquals(List.of("one", "two"), read(plain));
        assertEquals(List.of("one", "two"), read(gzip));
        assertEquals(List.of("one", "two"), read(zip));
    }

    @Test
    void skipsDirectoriesInZipSources() throws IOException {
        Path zip = temporaryDirectory.resolve("directories.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("entry\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertEquals(List.of("entry"), read(zip));
    }

    @Test
    void rejectsDirectoriesAsLineSources() {
        assertThrows(IOException.class, () -> GCLogSource.open(temporaryDirectory));
    }

    private List<String> read(Path source) throws IOException {
        try (var lines = GCLogSource.open(source)) {
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
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String name, String entryName, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

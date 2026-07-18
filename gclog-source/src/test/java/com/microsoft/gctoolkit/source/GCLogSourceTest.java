// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversFormatsAndPhysicalByteSizes() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("compressed.log.gz", "three\nfour\n");
        Path zip = writeZip("archive.zip", List.of("folder/", "folder/one.log", "two.log"),
                List.of("", "five\nsix\n", "seven\n"));

        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.format(temporaryDirectory));
        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.format(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.format(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.format(zip));
        assertEquals(Files.size(plain), GCLogSource.byteSize(plain));
        assertEquals(Files.size(gzip), GCLogSource.byteSize(gzip));
        assertEquals(Files.size(zip), GCLogSource.byteSize(zip));
    }

    @Test
    void discoversDirectoryAndZipSources() throws IOException {
        Path first = writePlain("first.log", "one\n");
        Path second = writePlain("second.log", "two\n");
        Path zip = writeZip("archive.zip", List.of("folder/", "folder/one.log", "two.log"),
                List.of("", "three\n", "four\n"));

        assertEquals(List.of(zip, first, second), GCLogSource.discover(temporaryDirectory));
        assertEquals(List.of("folder/one.log", "two.log"), GCLogSource.zipEntries(zip));
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("compressed.log.gz", "three\nfour\n");
        Path zip = writeZip("archive.zip", List.of("folder/", "folder/one.log", "two.log"),
                List.of("", "five\nsix\n", "seven\n"));

        assertEquals(List.of("one", "two"), read(GCLogSource.open(plain)));
        assertEquals(List.of("three", "four"), read(GCLogSource.open(gzip)));
        assertEquals(List.of("five", "six"), read(GCLogSource.open(zip)));
    }

    @Test
    void opensNamedAndMultipleZipEntries() throws IOException {
        Path zip = writeZip("archive.zip", List.of("one.log", "two.log"),
                List.of("one\ntwo\n", "three\nfour\n"));

        assertEquals(List.of("three", "four"), read(GCLogSource.openZipEntry(zip, "two.log")));
        assertEquals(List.of("one", "two", "three", "four"),
                read(GCLogSource.openZipEntries(zip, List.of("one.log", "two.log"))));
        assertThrows(IOException.class, () -> GCLogSource.openZipEntry(zip, "missing.log"));
    }

    @Test
    void rejectsUnsupportedOrMissingSources() throws IOException {
        Path emptyZip = writeZip("empty.zip", List.of(), List.of());
        Path zip = writeZip("archive.zip", List.of("one.log"), List.of("one\n"));
        Path missing = temporaryDirectory.resolve("missing.log");

        assertThrows(IOException.class, () -> GCLogSource.open(temporaryDirectory));
        assertThrows(IOException.class, () -> GCLogSource.open(emptyZip));
        assertThrows(IOException.class, () -> GCLogSource.openZipEntries(zip, List.of("one.log", "missing.log")));
        assertThrows(IOException.class, () -> GCLogSource.format(missing));
    }

    private List<String> read(java.util.stream.Stream<String> lines) {
        try (lines) {
            return lines.collect(toList());
        }
    }

    private Path writePlain(String fileName, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private Path writeGzip(String fileName, String content) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String fileName, List<String> names, List<String> contents) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (int index = 0; index < names.size(); index++) {
                output.putNextEntry(new ZipEntry(names.get(index)));
                output.write(contents.get(index).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}

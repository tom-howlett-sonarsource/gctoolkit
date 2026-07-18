// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    void discoversSourceFormatsByContent() throws IOException {
        Path plain = writePlain("plain.data", "plain");
        Path gzip = writeGzip("gzip.data", "gzip");
        Path zip = writeZip("zip.data", "logs/gc.log", "zip");

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.from(plain).format());
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.from(gzip).format());
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.from(zip).format());
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.from(temporaryDirectory).format());
    }

    @Test
    void reportsSourceByteSize() throws IOException {
        Path sourcePath = writePlain("gc.log", "first\nsecond\n");
        GCLogSource source = GCLogSource.from(sourcePath);

        assertEquals(sourcePath, source.path());
        assertEquals(Files.size(sourcePath), source.byteSize());
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = writePlain("plain.log", "plain\n");
        Path gzip = writeGzip("gzip.log", "gzip\n");
        Path zip = writeZip("zip.log", "logs/gc.log", "zip\n");

        assertEquals(List.of("plain"), read(GCLogSource.from(plain)));
        assertEquals(List.of("gzip"), read(GCLogSource.from(gzip)));
        assertEquals(List.of("zip"), read(GCLogSource.from(zip)));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        Path zipPath = temporaryDirectory.resolve("rotating.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            writeZipEntry(zip, "logs/gc.log.0", "zero\n");
            writeZipEntry(zip, "logs/gc.log.1", "one\n");
        }

        GCLogSource source = GCLogSource.from(zipPath);

        assertEquals(List.of("logs/gc.log.0", "logs/gc.log.1"), source.entries());
        try (var lines = source.open("logs/gc.log.1")) {
            assertEquals(List.of("one"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsUnsupportedOpenOperations() throws IOException {
        GCLogSource plainSource = GCLogSource.from(writePlain("plain.log", "plain"));

        assertEquals(List.of(), plainSource.entries());
        assertThrows(IOException.class, () -> plainSource.open("gc.log"));
        assertThrows(IOException.class, () -> GCLogSource.from(temporaryDirectory).open());
    }

    @Test
    void returnsEmptyStreamsForEmptyOrMissingZipEntries() throws IOException {
        Path zipPath = temporaryDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(zipPath))) {
        }
        GCLogSource source = GCLogSource.from(zipPath);

        assertEquals(List.of(), read(source));
        try (var lines = source.open("missing.log")) {
            assertEquals(List.of(), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void reportsMalformedZipAndMissingFileFailures() throws IOException {
        Path malformedZip = temporaryDirectory.resolve("malformed.zip");
        Files.write(malformedZip, new byte[]{0x50, 0x4b, 0x00});
        GCLogSource malformedSource = GCLogSource.from(malformedZip);
        Path missingPath = temporaryDirectory.resolve("missing.log");

        assertThrows(IOException.class, malformedSource::entries);
        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.from(missingPath).format());
        assertThrows(IOException.class, () -> GCLogSource.from(missingPath).open());
    }

    private List<String> read(GCLogSource source) throws IOException {
        try (var lines = source.open()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String fileName, String content) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }

    private Path writeGzip(String fileName, String content) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(path))) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String fileName, String entryName, String content) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(zip, entryName, content);
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

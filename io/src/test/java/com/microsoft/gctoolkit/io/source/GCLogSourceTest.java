// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
    void discoversSupportedSourceFormatsAndSizes() throws IOException {
        Path plain = writePlain("plain.log", "plain");
        Path gzip = writeGzip("gzip.log.gz", "gzip");
        Path zip = writeZip("zip.log.zip", List.of(new Entry("zip.log", "zip")));

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.from(plain).format());
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.from(gzip).format());
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.from(zip).format());
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.from(temporaryDirectory).format());
        assertEquals(Files.size(plain), GCLogSource.from(plain).size());
        assertEquals(Files.size(gzip), GCLogSource.from(gzip).size());
        assertEquals(Files.size(zip), GCLogSource.from(zip).size());
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = writePlain("plain.log", "first\nsecond\n");
        Path gzip = writeGzip("gzip.log.gz", "first\nsecond\n");
        Path zip = writeZip("zip.log.zip", List.of(
                new Entry("logs/", null),
                new Entry("logs/first.log", "first\nsecond\n"),
                new Entry("logs/ignored.log", "ignored\n")));

        assertEquals(List.of("first", "second"), lines(plain));
        assertEquals(List.of("first", "second"), lines(gzip));
        assertEquals(List.of("first", "second"), lines(zip));
    }

    @Test
    void discoversAndOpensNamedZipEntries() throws IOException {
        Path zip = writeZip("rotating.zip", List.of(
                new Entry("logs/", null),
                new Entry("logs/gc.log.0", "zero\n"),
                new Entry("logs/gc.log.1", "one\n")));
        GCLogSource source = GCLogSource.from(zip);

        assertEquals(List.of("logs/gc.log.0", "logs/gc.log.1"), source.zipEntries());
        try (var lines = source.lines("logs/gc.log.1")) {
            assertEquals(List.of("one"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> source.lines("missing.log"));
    }

    @Test
    void rejectsStreamingDirectories() {
        assertThrows(IOException.class, () -> GCLogSource.from(temporaryDirectory).lines());
    }

    private List<String> lines(Path path) throws IOException {
        try (var lines = GCLogSource.from(path).lines()) {
            return lines.collect(Collectors.toList());
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

    private Path writeZip(String fileName, List<Entry> entries) throws IOException {
        Path path = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (Entry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                if (entry.content != null) {
                    output.write(entry.content.getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }

    private static final class Entry {
        private final String name;
        private final String content;

        private Entry(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }
}

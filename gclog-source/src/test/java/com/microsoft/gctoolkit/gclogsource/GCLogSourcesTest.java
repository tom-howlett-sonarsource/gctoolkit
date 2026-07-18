// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourcesTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsSourceFormatsFromContent() throws IOException {
        Path plain = writePlain("gc.log", "plain");
        Path gzip = writeGzip("gc.data", "gzip");
        Path zip = writeZip("gc.archive", List.of("gc.log"), List.of("zip"));

        assertEquals(GCLogFileFormat.DIRECTORY, GCLogSources.formatOf(temporaryDirectory));
        assertEquals(GCLogFileFormat.PLAIN_TEXT, GCLogSources.formatOf(plain));
        assertEquals(GCLogFileFormat.GZIP, GCLogSources.formatOf(gzip));
        assertEquals(GCLogFileFormat.ZIP, GCLogSources.formatOf(zip));
    }

    @Test
    void opensPlainGzipAndFirstZipEntryAsLines() throws IOException {
        Path plain = writePlain("plain.log", "one\ntwo\n");
        Path gzip = writeGzip("gzip.log", "one\ntwo\n");
        Path zip = writeZip("zip.log", List.of("directory/", "directory/gc.log"), Arrays.asList(null, "one\ntwo\n"));

        assertEquals(List.of("one", "two"), lines(plain));
        assertEquals(List.of("one", "two"), lines(gzip));
        assertEquals(List.of("one", "two"), lines(zip));
    }

    @Test
    void discoversDirectorySiblingAndZipSources() throws IOException {
        Path current = writePlain("gc.log", "current");
        writePlain("gc.log.0", "zero");
        writePlain("other.log", "other");
        Path zip = writeZip("rotating.zip", List.of("directory/", "gc.log", "gc.log.0"), Arrays.asList(null, "current", "zero"));

        assertEquals(4, GCLogSources.inDirectory(temporaryDirectory).size());
        assertEquals(List.of(current, temporaryDirectory.resolve("gc.log.0")),
                GCLogSources.siblingsStartingWith(current, "gc.log"));
        assertEquals(List.of("gc.log", "gc.log.0"), GCLogSources.zipEntries(zip));
    }

    @Test
    void reportsSourceAndZipEntryByteSizes() throws IOException {
        Path plain = writePlain("sized.log", "12345");
        Path zip = writeZip("sized.zip", List.of("gc.log"), List.of("12345"));

        assertEquals(5L, GCLogSources.size(plain));
        assertEquals(5L, GCLogSources.size(zip, "gc.log"));
    }

    @Test
    void opensNamedZipEntriesAndRejectsMissingEntries() throws IOException {
        Path zip = writeZip("named.zip", List.of("first.log", "second.log"),
                List.of("first", "second"));

        try (var lines = GCLogSources.lines(zip, "second.log")) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class,
                () -> GCLogSources.open(zip, "missing.log"));
        assertThrows(IOException.class,
                () -> GCLogSources.size(zip, "missing.log"));
    }

    @Test
    void opensPlainBytesAndRejectsDirectories() throws IOException {
        Path plain = writePlain("bytes.log", "bytes");

        try (InputStream input = GCLogSources.open(plain)) {
            assertEquals("bytes", new String(input.readAllBytes(),
                    StandardCharsets.UTF_8));
        }
        assertThrows(IOException.class,
                () -> GCLogSources.open(temporaryDirectory));
        assertEquals(GCLogFileFormat.PLAIN_TEXT,
                GCLogSources.formatOf(temporaryDirectory.resolve("missing")));
    }

    private List<String> lines(Path path) throws IOException {
        try (var lines = GCLogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String name, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(name), content, StandardCharsets.UTF_8);
    }

    private Path writeGzip(String name, String content) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String name, List<String> entries, List<String> contents) throws IOException {
        Path path = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (int index = 0; index < entries.size(); index++) {
                output.putNextEntry(new ZipEntry(entries.get(index)));
                if (contents.get(index) != null) {
                    output.write(contents.get(index).getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }
}

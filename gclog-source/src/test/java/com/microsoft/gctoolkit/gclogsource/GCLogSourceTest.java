// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsFormatsFromContentRatherThanFileExtension() throws IOException {
        Path plain = writePlain("plain.zip", "plain");
        Path gzip = writeGzip("compressed.log", "gzip");
        Path zip = writeZip("archive.log", List.of(new ArchiveEntry("gc.log", "zip")));

        assertEquals(GCLogSourceFormat.DIRECTORY, GCLogSource.format(temporaryDirectory));
        assertEquals(GCLogSourceFormat.PLAIN_TEXT, GCLogSource.format(plain));
        assertEquals(GCLogSourceFormat.GZIP, GCLogSource.format(gzip));
        assertEquals(GCLogSourceFormat.ZIP, GCLogSource.format(zip));
    }

    @Test
    void discoversDirectoryContentsAndMatchingSiblings() throws IOException {
        Path current = writePlain("gc.log", "current");
        Path rotated = writePlain("gc.log.0", "rotated");
        writePlain("other.log", "other");
        Predicate<Path> gcLogs = path -> path.getFileName().toString().startsWith("gc.log");

        assertEquals(List.of(current, rotated), GCLogSource.discover(temporaryDirectory, gcLogs));
        assertEquals(List.of(current, rotated), GCLogSource.discover(current, gcLogs));
    }

    @Test
    void reportsPhysicalSourceSize() throws IOException {
        Path source = writePlain("gc.log", "123456789");

        assertEquals(Files.size(source), GCLogSource.size(source));
        assertThrows(IOException.class, () -> GCLogSource.size(temporaryDirectory));
    }

    @Test
    void discoversNonDirectoryZipEntries() throws IOException {
        Path zip = writeZip("archive.zip", List.of(
                new ArchiveEntry("logs/", null),
                new ArchiveEntry("logs/gc.log.0", "first"),
                new ArchiveEntry("logs/gc.log", "second")));

        assertEquals(List.of("logs/gc.log.0", "logs/gc.log"), GCLogSource.entries(zip));
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = writePlain("plain.log", "first\nsecond\n");
        Path gzip = writeGzip("compressed.gz", "first\nsecond\n");
        Path zip = writeZip("archive.zip", List.of(
                new ArchiveEntry("logs/", null),
                new ArchiveEntry("first.log", "first\nsecond\n"),
                new ArchiveEntry("ignored.log", "ignored\n")));

        assertEquals(List.of("first", "second"), read(GCLogSource.open(plain)));
        assertEquals(List.of("first", "second"), read(GCLogSource.openPlain(plain)));
        assertEquals(List.of("first", "second"), read(GCLogSource.open(gzip)));
        assertEquals(List.of("first", "second"), read(GCLogSource.open(zip)));
    }

    @Test
    void opensNamedZipEntry() throws IOException {
        Path zip = writeZip("archive.zip", List.of(
                new ArchiveEntry("first.log", "first\n"),
                new ArchiveEntry("second.log", "second\n")));

        assertEquals(List.of("second"), read(GCLogSource.open(zip, "second.log")));
        assertThrows(IOException.class, () -> GCLogSource.open(zip, "missing.log"));
    }

    private Path writePlain(String name, String content) throws IOException {
        return Files.writeString(temporaryDirectory.resolve(name), content, StandardCharsets.UTF_8);
    }

    private Path writeGzip(String name, String content) throws IOException {
        Path target = temporaryDirectory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(target))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return target;
    }

    private Path writeZip(String name, List<ArchiveEntry> entries) throws IOException {
        Path target = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            for (ArchiveEntry archiveEntry : entries) {
                output.putNextEntry(new ZipEntry(archiveEntry.name));
                if (archiveEntry.content != null) {
                    output.write(archiveEntry.content.getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return target;
    }

    private List<String> read(Stream<String> lines) {
        try (Stream<String> closeableLines = lines) {
            return closeableLines.collect(Collectors.toList());
        }
    }

    private static final class ArchiveEntry {
        private final String name;
        private final String content;

        private ArchiveEntry(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }
}

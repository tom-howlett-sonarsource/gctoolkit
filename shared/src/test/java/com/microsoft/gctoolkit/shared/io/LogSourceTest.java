// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversTypesFromFileContent() throws IOException {
        Path plain = writePlain("plain.data");
        Path gzip = writeGzip("gzip.data");
        Path zip = writeZip("zip.data");

        assertEquals(LogSource.Type.PLAIN_TEXT, LogSource.typeOf(plain));
        assertEquals(LogSource.Type.GZIP, LogSource.typeOf(gzip));
        assertEquals(LogSource.Type.ZIP, LogSource.typeOf(zip));
        assertEquals(LogSource.Type.DIRECTORY, LogSource.typeOf(directory));
    }

    @Test
    void reportsOnDiskByteSize() throws IOException {
        Path plain = writePlain("sized.log");

        assertEquals(Files.size(plain), LogSource.byteSize(plain));
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSource.byteSize(plain));
    }

    @Test
    void discoversDirectoryEntries() throws IOException {
        Path first = writePlain("first.log");
        Path second = writePlain("second.log");

        try (var paths = LogSource.discover(directory)) {
            assertEquals(Set.of(first, second), paths.collect(Collectors.toSet()));
        }
    }

    @Test
    void opensPlainGzipAndFirstZipFile() throws IOException {
        assertEquals(List.of("first line", "second line"), read(writePlain("plain.log")));
        assertEquals(List.of("first line", "second line"), read(writeGzip("compressed.log")));
        assertEquals(List.of("first line", "second line"), read(writeZip("archive.log")));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        Path zip = writeZip("entries.zip");

        assertEquals(List.of("logs/gc.log"), LogSource.zipEntries(zip));
        try (var lines = LogSource.openZipEntry(zip, "logs/gc.log")) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = LogSource.open(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip(String name) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String name) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

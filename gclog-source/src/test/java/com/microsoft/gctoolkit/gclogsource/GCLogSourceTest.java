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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogSourceTest {

    @TempDir
    Path directory;

    @Test
    void discoversSourceFormatsByContent() throws IOException {
        Path plain = write("plain.zip", "plain");
        Path gzip = gzip("compressed.log", "gzip");
        Path zip = zip("archive.log", "nested/", null, "nested/gc.log", "zip");

        assertEquals(LogSourceFormat.PLAIN_TEXT, GCLogSource.format(plain));
        assertEquals(LogSourceFormat.GZIP, GCLogSource.format(gzip));
        assertEquals(LogSourceFormat.ZIP, GCLogSource.format(zip));
        assertEquals(LogSourceFormat.DIRECTORY, GCLogSource.format(directory));
    }

    @Test
    void reportsFileAndDirectoryByteSizes() throws IOException {
        Path first = write("first.log", "1234");
        write("second.log", "56789");
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(4L, GCLogSource.size(first));
        assertEquals(9L, GCLogSource.size(directory));
    }

    @Test
    void discoversRegularFilesAndZipEntries() throws IOException {
        Path first = write("b.log", "b");
        Path second = write("a.log", "a");
        Files.createDirectory(directory.resolve("nested"));
        Path zip = zip("logs.zip", "folder/", null, "folder/two.log", "two", "one.log", "one");

        assertEquals(List.of(second, first, zip), GCLogSource.files(directory));
        assertEquals(List.of("folder/two.log", "one.log"), GCLogSource.zipEntries(zip));
    }

    @Test
    void opensPlainGzipAndFirstZipEntry() throws IOException {
        Path plain = write("plain.log", "plain");
        Path gzip = gzip("gzip.log", "gzip");
        Path zip = zip("zip.log", "folder/", null, "folder/gc.log", "zip");

        assertEquals("plain", read(plain));
        assertEquals("gzip", read(gzip));
        assertEquals("zip", read(zip));
    }

    @Test
    void opensNamedZipEntryAndRejectsMissingContent() throws IOException {
        Path zip = zip("logs.zip", "one.log", "one", "two.log", "two");
        Path emptyZip = zip("empty.zip", "folder/", null);

        try (var lines = GCLogSource.lines(zip, "two.log")) {
            assertEquals(List.of("two"), lines.collect(Collectors.toList()));
        }
        assertThrows(IOException.class, () -> GCLogSource.open(zip, "missing.log"));
        assertThrows(IOException.class, () -> GCLogSource.open(emptyZip));
        assertThrows(IOException.class, () -> GCLogSource.open(directory));
    }

    private String read(Path path) throws IOException {
        try (var input = GCLogSource.open(path)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path write(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private Path gzip(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String name, String... entries) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (int index = 0; index < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                if (entries[index + 1] != null) {
                    output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }
}

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

    private static final String CONTENT = "[0.001s][info][gc] test\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatsFromContentAndReportsSourceByteSize() throws IOException {
        Path plain = writePlain("plain.gz");
        Path gzip = writeGzip("gzip.log");
        Path zip = writeZip("zip.log");

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.detectFormat(plain));
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.detectFormat(gzip));
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.detectFormat(zip));

        GCLogSource source = GCLogSource.discover(gzip);
        assertEquals(gzip, source.getPath());
        assertEquals(Files.size(gzip), source.getByteSize());
        assertEquals(Files.size(gzip), GCLogSource.byteSize(gzip));
    }

    @Test
    void opensPlainGzipAndFirstNonDirectoryZipEntry() throws IOException {
        assertEquals(List.of(CONTENT.trim()), read(writePlain("plain.log")));
        assertEquals(List.of(CONTENT.trim()), read(writeGzip("gzip.log.gz")));
        assertEquals(List.of(CONTENT.trim()), read(writeZip("zip.log.zip")));
    }

    @Test
    void rejectsDirectoriesAsLineSources() throws IOException {
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.detectFormat(directory));
        assertThrows(IOException.class, () -> GCLogSource.open(directory));
    }

    private List<String> read(Path path) throws IOException {
        try (var lines = GCLogSource.open(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlain(String name) throws IOException {
        return Files.writeString(directory.resolve(name), CONTENT, StandardCharsets.UTF_8);
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

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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndStreamsPlainZipAndGZipSourcesByContent() throws IOException {
        assertSource(writePlain("plain.data"), SourceType.PLAIN);
        assertSource(writeZip("zip.data"), SourceType.ZIP);
        assertSource(writeGZip("gzip.data"), SourceType.GZIP);
    }

    @Test
    void reportsSourceSizeInBytes() throws IOException {
        Path sourcePath = writePlain("sized.log");

        assertEquals(Files.size(sourcePath), LogFileSource.from(sourcePath).size());
    }

    private void assertSource(Path sourcePath, SourceType type) throws IOException {
        LogFileSource source = LogFileSource.from(sourcePath);
        assertEquals(type == SourceType.PLAIN, source.isPlainText());
        assertEquals(type == SourceType.ZIP, source.isZip());
        assertEquals(type == SourceType.GZIP, source.isGZip());
        try (var lines = source.stream()) {
            assertEquals(List.of("first line", "second line"), lines.collect(toList()));
        }
    }

    private Path writePlain(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeZip(String name) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("directory/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }

    private Path writeGZip(String name) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private enum SourceType {
        PLAIN,
        ZIP,
        GZIP
    }
}

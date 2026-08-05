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
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileSourceTest {

    private static final String CONTENT = "first line\nsecond line\n";

    @TempDir
    Path directory;

    @Test
    void discoversAndOpensSupportedSources() throws IOException {
        Path plain = writePlain();
        Path gzip = writeGzip();
        Path zip = writeZip();

        assertEquals(LogFileSource.Format.PLAIN_TEXT, LogFileSource.discover(plain));
        assertEquals(LogFileSource.Format.GZIP, LogFileSource.discover(gzip));
        assertEquals(LogFileSource.Format.ZIP, LogFileSource.discover(zip));
        assertEquals(LogFileSource.Format.DIRECTORY, LogFileSource.discover(directory));

        assertLines(plain);
        assertLines(gzip);
        assertLines(zip);
    }

    @Test
    void measuresFilesAndDirectorySources() throws IOException {
        Path plain = writePlain();
        Path nestedDirectory = Files.createDirectory(directory.resolve("nested"));
        Path nested = Files.writeString(nestedDirectory.resolve("nested.log"), CONTENT,
                StandardCharsets.UTF_8);

        assertEquals(Files.size(plain), LogFileSource.byteSize(plain));
        assertEquals(Files.size(plain) + Files.size(nested), LogFileSource.byteSize(directory));
    }

    @Test
    void listsAndOpensNamedZipEntries() throws IOException {
        Path zip = writeZip();

        assertEquals(List.of("gc.log"), LogFileSource.zipEntries(zip));
        try (var lines = LogFileSource.openZipEntry(zip, "gc.log")) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private void assertLines(Path source) throws IOException {
        try (var lines = LogFileSource.open(source)) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlain() throws IOException {
        return Files.writeString(directory.resolve("plain.log"), CONTENT, StandardCharsets.UTF_8);
    }

    private Path writeGzip() throws IOException {
        Path source = directory.resolve("compressed.log");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(source))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return source;
    }

    private Path writeZip() throws IOException {
        Path source = directory.resolve("archived.log");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(source))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return source;
    }
}

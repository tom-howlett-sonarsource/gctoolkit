// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    void discoversPlainZipGzipAndDirectorySources() throws IOException {
        Path plain = writePlainLog();
        Path zip = writeZipLog();
        Path gzip = writeGzipLog();

        assertEquals(GCLogSource.Format.PLAIN_TEXT, GCLogSource.from(plain).format());
        assertEquals(GCLogSource.Format.ZIP, GCLogSource.from(zip).format());
        assertEquals(GCLogSource.Format.GZIP, GCLogSource.from(gzip).format());
        assertEquals(GCLogSource.Format.DIRECTORY, GCLogSource.from(temporaryDirectory).format());
    }

    @Test
    void reportsPhysicalSourceSizeInBytes() throws IOException {
        Path plain = writePlainLog();
        Path zip = writeZipLog();
        Path gzip = writeGzipLog();

        assertEquals(Files.size(plain), GCLogSource.from(plain).size());
        assertEquals(Files.size(zip), GCLogSource.from(zip).size());
        assertEquals(Files.size(gzip), GCLogSource.from(gzip).size());
    }

    @Test
    void opensPlainZipAndGzipLogLines() throws IOException {
        assertLines(writePlainLog());
        assertLines(writeZipLog());
        assertLines(writeGzipLog());
    }

    @Test
    void rejectsDirectoriesAndZipFilesWithoutLogEntries() throws IOException {
        Path emptyZip = temporaryDirectory.resolve("empty.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> GCLogSource.from(temporaryDirectory).lines());
        assertThrows(IOException.class, () -> GCLogSource.from(emptyZip).lines());
    }

    private void assertLines(Path path) throws IOException {
        try (var lines = GCLogSource.from(path).lines()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlainLog() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log");
        Files.writeString(path, "first\nsecond\n");
        return path;
    }

    private Path writeZipLog() throws IOException {
        Path path = temporaryDirectory.resolve("gc.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write("first\nsecond\n".getBytes());
            output.closeEntry();
        }
        return path;
    }

    private Path writeGzipLog() throws IOException {
        Path path = temporaryDirectory.resolve("gc.log.gz");
        try (var output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write("first\nsecond\n".getBytes());
        }
        return path;
    }
}

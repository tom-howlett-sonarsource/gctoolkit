// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {
    private static final String CONTENT = "line one\nline two\n";
    private static final byte[] BYTES = CONTENT.getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void discoversFormats() throws IOException {
        assertEquals(LogSources.Format.DIRECTORY, LogSources.discover(directory));
        assertEquals(LogSources.Format.PLAINTEXT, LogSources.discover(plain()));
        assertEquals(LogSources.Format.GZIP, LogSources.discover(gzip()));
        assertEquals(LogSources.Format.ZIP, LogSources.discover(zip(true)));
    }

    @Test
    void shortOrMissingFilesAreTreatedAsPlainText() throws IOException {
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogSources.Format.PLAINTEXT, LogSources.discover(empty));
        assertEquals(LogSources.Format.PLAINTEXT, LogSources.discover(directory.resolve("missing.log")));
    }

    @Test
    void linesReadsEveryFormat() throws IOException {
        for (Path path : List.of(plain(), gzip(), zip(true), zip(false)))
            assertEquals(List.of("line one", "line two"), read(path), path.toString());
    }

    @Test
    void emptyZipHasNoLines() throws IOException {
        Path path = directory.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
        }
        assertTrue(read(path).isEmpty());
    }

    @Test
    void openingADirectoryFails() {
        assertThrows(IOException.class, () -> LogSources.open(directory));
        assertThrows(IOException.class, () -> LogSources.lines(directory));
    }

    @Test
    void sizesFilesAndDirectories() throws IOException {
        Path plain = plain();
        assertEquals(BYTES.length, LogSources.size(plain));
        Files.createDirectory(directory.resolve("nested"));
        Path other = Files.write(directory.resolve("other.log"), BYTES);
        assertEquals(Files.size(plain) + Files.size(other), LogSources.size(directory));
    }

    @Test
    void sizingMissingFileFails() {
        assertThrows(IOException.class, () -> LogSources.size(directory.resolve("missing.log")));
    }

    private List<String> read(Path path) throws IOException {
        try (Stream<String> lines = LogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plain() throws IOException {
        return Files.write(directory.resolve("gc.log"), BYTES);
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(BYTES);
        }
        return path;
    }

    private Path zip(boolean withDirectoryEntry) throws IOException {
        Path path = directory.resolve(withDirectoryEntry ? "dir.zip" : "flat.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            if (withDirectoryEntry) {
                out.putNextEntry(new ZipEntry("logs/"));
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write(BYTES);
            out.closeEntry();
        }
        return path;
    }
}

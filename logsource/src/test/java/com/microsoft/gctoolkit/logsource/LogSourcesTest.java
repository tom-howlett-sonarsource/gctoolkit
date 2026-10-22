// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(plain()));
        assertEquals(LogSourceFormat.GZIP, LogSources.discover(gzip()));
        assertEquals(LogSourceFormat.ZIP, LogSources.discover(zip(true)));
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.discover(directory));
    }

    @Test
    void unreadableOrEmptySourceIsPlainText() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(directory.resolve("missing.log")));
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(empty));
    }

    @Test
    void sizesFilesAndDirectories() throws IOException {
        Path plain = plain();
        Path gzip = gzip();
        Files.createDirectory(directory.resolve("nested"));
        assertEquals(BYTES.length, LogSources.size(plain));
        assertEquals(Files.size(plain) + Files.size(gzip), LogSources.size(directory));
        assertThrows(IOException.class, () -> LogSources.size(directory.resolve("missing.log")));
    }

    @Test
    void opensAllFileFormats() throws IOException {
        for (Path source : List.of(plain(), gzip(), zip(true))) {
            try (InputStream in = LogSources.open(source)) {
                assertArrayEquals(BYTES, in.readAllBytes(), source.toString());
            }
        }
    }

    @Test
    void streamsLinesOfAllFileFormats() throws IOException {
        for (Path source : List.of(plain(), gzip(), zip(true))) {
            try (Stream<String> lines = LogSources.lines(source)) {
                assertEquals(List.of("line one", "line two"), lines.collect(Collectors.toList()), source.toString());
            }
        }
    }

    @Test
    void zipWithoutFileEntriesIsEmpty() throws IOException {
        try (Stream<String> lines = LogSources.lines(zip(false))) {
            assertTrue(lines.findAny().isEmpty());
        }
    }

    @Test
    void rejectsDirectoriesAndCorruptArchives() throws IOException {
        assertThrows(IOException.class, () -> LogSources.open(directory));
        assertThrows(IOException.class, () -> LogSources.lines(directory));
        Path corrupt = directory.resolve("corrupt.gz");
        Files.write(corrupt, new byte[]{0x1F, (byte) 0x8b, 0x00});
        assertThrows(IOException.class, () -> LogSources.open(corrupt));
    }

    @Test
    void rejectsNullPath() {
        assertThrows(NullPointerException.class, () -> LogSources.discover(null));
        assertThrows(NullPointerException.class, () -> LogSources.size(null));
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

    private Path zip(boolean withFile) throws IOException {
        Path path = directory.resolve(withFile ? "gc.zip" : "empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            if (withFile) {
                out.putNextEntry(new ZipEntry("logs/gc.log"));
                out.write(BYTES);
                out.closeEntry();
            }
        }
        return path;
    }
}

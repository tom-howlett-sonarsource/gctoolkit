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
    private static final List<String> LINES = List.of("line one", "line two");

    @TempDir
    Path directory;

    @Test
    void discoversFormats() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discoverFormat(plain()));
        assertEquals(LogSourceFormat.GZIP, LogSources.discoverFormat(gzip()));
        assertEquals(LogSourceFormat.ZIP, LogSources.discoverFormat(zip()));
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.discoverFormat(directory));
    }

    @Test
    void unreadableOrTinySourcesAreTreatedAsPlainText() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discoverFormat(directory.resolve("missing.log")));
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discoverFormat(empty));
    }

    @Test
    void streamsLinesFromAllFileFormats() throws IOException {
        assertEquals(LINES, read(plain()));
        assertEquals(LINES, read(gzip()));
        assertEquals(LINES, read(zip()));
    }

    @Test
    void zipWithoutFileEntriesYieldsNoLines() throws IOException {
        Path path = directory.resolve("dirs-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("only/"));
            output.closeEntry();
        }
        assertTrue(read(path).isEmpty());
    }

    @Test
    void directoriesCannotBeOpened() {
        assertThrows(IOException.class, () -> LogSources.open(directory));
    }

    @Test
    void corruptGzipIsReported() throws IOException {
        Path path = directory.resolve("bad.gz");
        Files.write(path, new byte[]{0x1F, (byte) 0x8b, 0x00});
        assertThrows(IOException.class, () -> LogSources.open(path));
    }

    @Test
    void sizesSourcesInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSources.sizeInBytes(plain()));
        assertThrows(IOException.class, () -> LogSources.sizeInBytes(directory.resolve("missing.log")));
        assertThrows(NullPointerException.class, () -> LogSources.sizeInBytes(null));
    }

    private List<String> read(Path path) throws IOException {
        try (Stream<String> lines = LogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plain() throws IOException {
        return Files.writeString(directory.resolve("gc.log"), CONTENT, StandardCharsets.UTF_8);
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
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

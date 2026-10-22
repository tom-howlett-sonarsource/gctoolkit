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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {

    private static final String CONTENT = "line one\nline two\n";
    private static final List<String> LINES = List.of("line one", "line two");

    @TempDir
    Path directory;

    @Test
    void detectsFormats() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(plain("gc.log")));
        assertEquals(LogSourceFormat.GZIP, LogSources.formatOf(gzip()));
        assertEquals(LogSourceFormat.ZIP, LogSources.formatOf(zip()));
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.formatOf(directory));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(directory.resolve("missing")));
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.formatOf(Files.createFile(directory.resolve("empty"))));
    }

    @Test
    void streamsAllFormats() throws IOException {
        assertEquals(LINES, read(plain("gc.log")));
        assertEquals(LINES, read(gzip()));
        assertEquals(LINES, read(zip()));
    }

    @Test
    void zipWithoutFileEntriesIsEmpty() throws IOException {
        Path path = directory.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("dir/"));
            out.closeEntry();
        }
        assertTrue(read(path).isEmpty());
    }

    @Test
    void openRejectsDirectoriesAndMissingFiles() {
        assertThrows(IOException.class, () -> LogSources.open(directory));
        assertThrows(IOException.class, () -> LogSources.open(directory.resolve("missing")));
    }

    @Test
    void openClosesFileWhenDecompressionFails() throws IOException {
        Path corrupt = directory.resolve("corrupt.gz");
        Files.write(corrupt, new byte[]{0x1F, (byte) 0x8b});
        assertThrows(IOException.class, () -> LogSources.open(corrupt));
        Files.delete(corrupt);
    }

    @Test
    void sizesFilesAndDirectories() throws IOException {
        Path log = plain("gc.log");
        Files.createDirectory(directory.resolve("sub"));
        long expected = CONTENT.getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expected, LogSources.sizeOf(log));
        plain("gc.log.1");
        assertEquals(2 * expected, LogSources.sizeOf(directory));
    }

    @Test
    void listsDirectoryEntries() throws IOException {
        Path log = plain("gc.log");
        assertEquals(List.of(log), LogSources.list(directory));
        assertThrows(IOException.class, () -> LogSources.list(directory.resolve("missing")));
    }

    @Test
    void closingLinesClosesSource() throws IOException {
        Path log = plain("gc.log");
        Stream<String> lines = LogSources.lines(log);
        lines.close();
        try (InputStream in = LogSources.open(log)) {
            assertEquals('l', in.read());
        }
    }

    private List<String> read(Path path) throws IOException {
        try (Stream<String> lines = LogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plain(String name) throws IOException {
        return Files.writeString(directory.resolve(name), CONTENT, StandardCharsets.UTF_8);
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/gc.log"));
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return path;
    }
}

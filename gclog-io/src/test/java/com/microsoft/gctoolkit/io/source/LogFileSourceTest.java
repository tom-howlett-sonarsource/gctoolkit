// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileSourceTest {

    private static final String LINE = "0.001: a log line";
    private static final String CONTENT = LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainText() throws IOException {
        assertEquals(LogFileFormat.Kind.PLAINTEXT, LogFileFormat.discover(writePlain()));
    }

    @Test
    void discoversGzip() throws IOException {
        assertEquals(LogFileFormat.Kind.GZIP, LogFileFormat.discover(writeGzip()));
    }

    @Test
    void discoversZip() throws IOException {
        assertEquals(LogFileFormat.Kind.ZIP, LogFileFormat.discover(writeZip()));
    }

    @Test
    void discoversDirectory() {
        assertEquals(LogFileFormat.Kind.DIRECTORY, LogFileFormat.discover(directory));
    }

    @Test
    void magicMatchesAndRejects() throws IOException {
        Path gzip = writeGzip();
        assertTrue(LogFileFormat.magic(gzip, 0x1F, 0x8b));
        assertFalse(LogFileFormat.magic(gzip, 0x50, 0x4b));
    }

    @Test
    void magicReturnsFalseForMissingFile() {
        assertFalse(LogFileFormat.magic(directory.resolve("absent.log"), 0x1F, 0x8b));
    }

    @Test
    void streamsPlainText() throws IOException {
        assertContainsLine(LogFileStreams.plainText(writePlain()));
    }

    @Test
    void streamsGzip() throws IOException {
        assertContainsLine(LogFileStreams.gzip(writeGzip()));
    }

    @Test
    void streamsZip() throws IOException {
        assertContainsLine(LogFileStreams.zip(writeZip()));
    }

    private void assertContainsLine(Stream<String> stream) {
        try (stream) {
            List<String> collected = stream.map(String::trim).collect(Collectors.toList());
            assertTrue(collected.contains(LINE));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

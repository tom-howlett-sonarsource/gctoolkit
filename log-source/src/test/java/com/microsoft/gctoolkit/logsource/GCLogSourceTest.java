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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String LINE = "[0.001s][info][gc] hello";
    private static final String CONTENT = LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainTextGzipZipAndDirectory() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discover(writePlain()));
        assertEquals(LogFileFormat.GZIP, GCLogSource.discover(writeGzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSource.discover(writeZip()));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSource.discover(directory));
    }

    @Test
    void matchesMagicReadsLeadingBytes() throws IOException {
        Path gzip = writeGzip();
        assertTrue(GCLogSource.matchesMagic(gzip, 0x1F, 0x8b));
        assertFalse(GCLogSource.matchesMagic(gzip, 0x50, 0x4b));
    }

    @Test
    void matchesMagicReturnsFalseForMissingFile() {
        assertFalse(GCLogSource.matchesMagic(directory.resolve("absent.log"), 0x1F, 0x8b));
    }

    @Test
    void opensPlainZipAndGzipByFormat() throws IOException {
        assertContains(GCLogSource.open(writePlain(), LogFileFormat.PLAINTEXT));
        assertContains(GCLogSource.open(writeZip(), LogFileFormat.ZIP));
        assertContains(GCLogSource.open(writeGzip(), LogFileFormat.GZIP));
    }

    @Test
    void openEndToEndUsesDiscoveredFormat() throws IOException {
        Path plain = writePlain();
        assertContains(GCLogSource.open(plain, GCLogSource.discover(plain)));
        Path gzip = writeGzip();
        assertContains(GCLogSource.open(gzip, GCLogSource.discover(gzip)));
    }

    @Test
    void openPlainZipGzipHelpers() throws IOException {
        assertContains(GCLogSource.openPlain(writePlain()));
        assertContains(GCLogSource.openZip(writeZip()));
        assertContains(GCLogSource.openGZip(writeGzip()));
    }

    @Test
    void openRejectsUnknownAndDirectoryFormats() {
        assertThrows(IOException.class, () -> GCLogSource.open(directory, LogFileFormat.UNKNOWN));
        assertThrows(IOException.class, () -> GCLogSource.open(directory, LogFileFormat.DIRECTORY));
    }

    private void assertContains(Stream<String> stream) {
        try (stream) {
            List<String> lines = stream.map(String::trim).collect(Collectors.toList());
            assertTrue(lines.contains(LINE), () -> "expected to contain log line, got " + lines);
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

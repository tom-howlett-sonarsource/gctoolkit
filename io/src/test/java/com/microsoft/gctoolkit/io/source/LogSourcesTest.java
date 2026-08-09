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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {

    private static final String LINE = "[0.001s][info][gc] hello";
    private static final String CONTENT = LINE + "\n";

    @TempDir
    Path dir;

    @Test
    void detectsPlainTextGzipZipAndDirectory() throws IOException {
        Path plain = writePlain("plain.log");
        Path gzip = writeGzip("gz.log.gz");
        Path zip = writeZip("z.log.zip");
        assertEquals(LogFileFormat.PLAINTEXT, LogSources.detectFormat(plain));
        assertEquals(LogFileFormat.GZIP, LogSources.detectFormat(gzip));
        assertEquals(LogFileFormat.ZIP, LogSources.detectFormat(zip));
        assertEquals(LogFileFormat.DIRECTORY, LogSources.detectFormat(dir));
        assertEquals(LogFileFormat.UNKNOWN, LogSources.detectFormat(null));
    }

    @Test
    void magicByteMatchDistinguishesGzipFromZip() throws IOException {
        Path gzip = writeGzip("m.log.gz");
        assertTrue(LogSources.matchesMagic(gzip, LogSources.GZIP_MAGIC1, LogSources.GZIP_MAGIC2));
        assertFalse(LogSources.matchesMagic(gzip, LogSources.ZIP_MAGIC1, LogSources.ZIP_MAGIC2));
    }

    @Test
    void byteSizeReturnsSizeInBytes() throws IOException {
        Path plain = writePlain("size.log");
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSources.byteSize(plain));
        assertEquals(-1L, LogSources.byteSize(null));
    }

    @Test
    void openStreamsPlainZipAndGzipLines() throws IOException {
        assertContainsLine(LogSources.open(writePlain("p.log")));
        assertContainsLine(LogSources.open(writeGzip("g.log.gz")));
        assertContainsLine(LogSources.open(writeZip("z.log.zip")));
    }

    @Test
    void openRejectsDirectoryAndUnknown() throws IOException {
        assertThrows(IOException.class, () -> LogSources.open(dir));
    }

    private void assertContainsLine(Stream<String> stream) {
        try (var s = stream) {
            List<String> collected = s.collect(Collectors.toList());
            assertTrue(collected.contains(LINE), "stream missing expected line: " + collected);
        }
    }

    private Path writePlain(String name) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, CONTENT, StandardCharsets.UTF_8);
        return p;
    }

    private Path writeGzip(String name) throws IOException {
        Path p = dir.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(p))) {
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return p;
    }

    private Path writeZip(String name) throws IOException {
        Path p = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(p))) {
            out.putNextEntry(new ZipEntry("entry.log"));
            out.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return p;
    }
}

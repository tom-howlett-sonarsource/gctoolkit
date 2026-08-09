// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.common;

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

    private static final String LINE = "[0.001s][info][gc] hello";

    @TempDir
    Path directory;

    @Test
    void discoversPlainTextFormat() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, LINE + "\n", StandardCharsets.UTF_8);
        assertEquals(SourceFormat.PLAINTEXT, LogSources.discoverFormat(plain));
    }

    @Test
    void discoversGzipFormat() throws IOException {
        Path gz = directory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write((LINE + "\n").getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(SourceFormat.GZIP, LogSources.discoverFormat(gz));
    }

    @Test
    void discoversZipFormat() throws IOException {
        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write((LINE + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(SourceFormat.ZIP, LogSources.discoverFormat(zip));
    }

    @Test
    void discoversDirectoryFormat() {
        assertEquals(SourceFormat.DIRECTORY, LogSources.discoverFormat(directory));
    }

    @Test
    void opensPlainZipAndGzipStreams() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.writeString(plain, LINE + "\n", StandardCharsets.UTF_8);
        assertContainsLine(LogSources.open(plain));

        Path gz = directory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write((LINE + "\n").getBytes(StandardCharsets.UTF_8));
        }
        assertContainsLine(LogSources.open(gz));

        Path zip = directory.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write((LINE + "\n").getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertContainsLine(LogSources.open(zip));
    }

    @Test
    void openRejectsDirectoryAndUnknown() {
        assertThrows(IOException.class, () -> LogSources.open(directory));
    }

    @Test
    void byteSizeMatchesFileLength() throws IOException {
        Path plain = directory.resolve("gc.log");
        byte[] payload = (LINE + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(plain, payload);
        assertEquals(payload.length, LogSources.byteSize(plain));
    }

    @Test
    void byteSizeIsZeroForDirectories() {
        assertEquals(0L, LogSources.byteSize(directory));
    }

    @Test
    void matchesMagicChecksLeadingBytes() throws IOException {
        Path plain = directory.resolve("gc.log");
        Files.write(plain, new byte[]{0x1F, (byte) 0x8B, 0x00});
        assertTrue(LogSources.matchesMagic(plain, 0x1F, 0x8B));
    }

    private void assertContainsLine(Stream<String> lines) {
        try (Stream<String> owned = lines) {
            List<String> collected = owned.collect(Collectors.toList());
            assertTrue(collected.contains(LINE));
        }
    }
}

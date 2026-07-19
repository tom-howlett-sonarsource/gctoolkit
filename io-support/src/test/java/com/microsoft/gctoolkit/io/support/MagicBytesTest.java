// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MagicBytesTest {

    @Test
    void detectsPlainTextFile(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.log");
        Files.writeString(plain, "hello\nworld\n");
        assertEquals(LogStreamFormat.PLAINTEXT, MagicBytes.detectFormat(plain));
    }

    @Test
    void detectsGzipFile(@TempDir Path tempDir) throws IOException {
        Path gz = tempDir.resolve("plain.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("gzip content\n".getBytes());
        }
        assertEquals(LogStreamFormat.GZIP, MagicBytes.detectFormat(gz));
    }

    @Test
    void detectsZipFile(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("plain.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("entry.log"));
            out.write("zip content\n".getBytes());
            out.closeEntry();
        }
        assertEquals(LogStreamFormat.ZIP, MagicBytes.detectFormat(zip));
    }

    @Test
    void detectsDirectory(@TempDir Path tempDir) {
        assertEquals(LogStreamFormat.DIRECTORY, MagicBytes.detectFormat(tempDir));
    }

    @Test
    void matchesReturnsTrueForMatchingBytes(@TempDir Path tempDir) throws IOException {
        Path gz = tempDir.resolve("g.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("x".getBytes());
        }
        assertTrue(MagicBytes.matches(gz, MagicBytes.GZIP_MAGIC1, MagicBytes.GZIP_MAGIC2));
    }

    @Test
    void matchesReturnsFalseForMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        assertFalse(MagicBytes.matches(missing, MagicBytes.GZIP_MAGIC1, MagicBytes.GZIP_MAGIC2));
    }

    @Test
    void matchesReturnsFalseForShortFile(@TempDir Path tempDir) throws IOException {
        Path shortFile = tempDir.resolve("short");
        Files.write(shortFile, new byte[]{0x1F});
        assertFalse(MagicBytes.matches(shortFile, MagicBytes.GZIP_MAGIC1, MagicBytes.GZIP_MAGIC2));
    }
}

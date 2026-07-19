// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileMagicTest {

    @Test
    void plainTextIsDetected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.log");
        Files.writeString(file, "hello");
        assertEquals(LogFileFormat.PLAINTEXT, LogFileMagic.detect(file));
    }

    @Test
    void directoryIsDetected(@TempDir Path dir) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileMagic.detect(dir));
    }

    @Test
    void gzipIsDetected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("some.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogFileFormat.GZIP, LogFileMagic.detect(file));
    }

    @Test
    void zipIsDetected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("some.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("inner.log"));
            out.write("hello".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(LogFileFormat.ZIP, LogFileMagic.detect(file));
    }

    @Test
    void matchesRecognisesExactSignature(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.log");
        Files.write(file, new byte[] { (byte) 0x1F, (byte) 0x8B, 0x00 });
        assertTrue(LogFileMagic.matches(file, LogFileMagic.GZIP_MAGIC1, LogFileMagic.GZIP_MAGIC2));
        assertFalse(LogFileMagic.matches(file, LogFileMagic.ZIP_MAGIC1, LogFileMagic.ZIP_MAGIC2));
    }

    @Test
    void missingFileMatchesNothing(@TempDir Path dir) {
        Path file = dir.resolve("missing.log");
        assertFalse(LogFileMagic.matches(file, LogFileMagic.GZIP_MAGIC1, LogFileMagic.GZIP_MAGIC2));
    }
}

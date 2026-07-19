// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
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

class LogFileFormatDetectorTest {

    @Test
    void detectsPlainText(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("log.txt");
        Files.writeString(file, "hello\nworld\n");
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsDirectory(@TempDir Path tmp) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormatDetector.detect(tmp));
    }

    @Test
    void detectsGZip(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("log.gz");
        try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.write("hello\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogFileFormat.GZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsZip(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            zos.putNextEntry(new ZipEntry("log.txt"));
            zos.write("hello\n".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertEquals(LogFileFormat.ZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectHandlesNullPath() {
        assertEquals(LogFileFormat.UNKNOWN, LogFileFormatDetector.detect(null));
    }

    @Test
    void matchesMagicReturnsTrueForMatchingBytes(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("magic.bin");
        Files.write(file, new byte[]{0x1F, (byte) 0x8B, 0x00});
        assertTrue(LogFileFormatDetector.matchesMagic(file,
                LogFileFormatDetector.GZIP_MAGIC1, LogFileFormatDetector.GZIP_MAGIC2));
    }

    @Test
    void matchesMagicReturnsFalseForMismatch(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("magic.bin");
        Files.write(file, new byte[]{0x00, 0x00});
        assertFalse(LogFileFormatDetector.matchesMagic(file,
                LogFileFormatDetector.ZIP_MAGIC1, LogFileFormatDetector.ZIP_MAGIC2));
    }

    @Test
    void matchesMagicReturnsFalseWhenFileMissing(@TempDir Path tmp) {
        Path missing = tmp.resolve("missing.bin");
        assertFalse(LogFileFormatDetector.matchesMagic(missing,
                LogFileFormatDetector.GZIP_MAGIC1, LogFileFormatDetector.GZIP_MAGIC2));
    }
}

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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileFormatDetectorTest {

    @Test
    void detectsDirectory(@TempDir Path dir) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormatDetector.detect(dir));
    }

    @Test
    void detectsPlainText(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("plain.log");
        Files.write(file, "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsZip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("inside.log"));
            out.write("body".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(LogFileFormat.ZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsGZip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("body".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(LogFileFormat.GZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void magicMatchesFirstTwoBytes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bytes.bin");
        Files.write(file, new byte[]{(byte) 0xCA, (byte) 0xFE, 0x00});
        assertTrue(LogFileFormatDetector.hasMagic(file, 0xCA, 0xFE));
        assertFalse(LogFileFormatDetector.hasMagic(file, 0xCA, 0xFF));
    }

    @Test
    void magicOnMissingFileIsFalse(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist");
        assertFalse(LogFileFormatDetector.hasMagic(missing, 0x00, 0x00));
    }

    @Test
    void enumValuesAreStable() {
        assertEquals(5, LogFileFormat.values().length);
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormat.valueOf("PLAINTEXT"));
    }
}

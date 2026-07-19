// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.sources.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
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
    void detectsDirectory(@TempDir Path tempDir) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormatDetector.detect(tempDir));
    }

    @Test
    void detectsPlainText(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log");
        Files.writeString(file, "hello world\n");
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsGZip(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("hello world\n".getBytes());
        }
        assertEquals(LogFileFormat.GZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsZip(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("entry.log"));
            out.write("hello world\n".getBytes());
            out.closeEntry();
        }
        assertEquals(LogFileFormat.ZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void matchesMagicHandlesMissingFile(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nope");
        assertFalse(LogFileFormatDetector.matchesMagic(missing,
                LogFileFormatDetector.GZIP_MAGIC1, LogFileFormatDetector.GZIP_MAGIC2));
    }

    @Test
    void matchesMagicExposesGZipHeader(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("plain.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("hello".getBytes());
        }
        assertTrue(LogFileFormatDetector.matchesMagic(file,
                LogFileFormatDetector.GZIP_MAGIC1, LogFileFormatDetector.GZIP_MAGIC2));
        assertFalse(LogFileFormatDetector.matchesMagic(file,
                LogFileFormatDetector.ZIP_MAGIC1, LogFileFormatDetector.ZIP_MAGIC2));
    }
}

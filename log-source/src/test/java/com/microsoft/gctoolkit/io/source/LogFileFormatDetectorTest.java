// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

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
    void detectsDirectory(@TempDir Path dir) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormatDetector.detect(dir));
    }

    @Test
    void detectsPlainText(@TempDir Path dir) throws IOException {
        Path file = Files.writeString(dir.resolve("plain.log"), "hello\nworld\n");
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsGZip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("compressed.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(file))) {
            out.write("hello\n".getBytes());
        }
        assertEquals(LogFileFormat.GZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void detectsZip(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("archive.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("hello\n".getBytes());
            out.closeEntry();
        }
        assertEquals(LogFileFormat.ZIP, LogFileFormatDetector.detect(file));
    }

    @Test
    void hasMagicMatchesLeadingBytes(@TempDir Path dir) throws IOException {
        Path file = Files.write(dir.resolve("bytes.bin"), new byte[] { (byte) 0xAB, (byte) 0xCD, 0x01, 0x02 });
        assertTrue(LogFileFormatDetector.hasMagic(file, 0xAB, 0xCD));
        assertFalse(LogFileFormatDetector.hasMagic(file, 0xAB, 0x00));
    }

    @Test
    void hasMagicSwallowsMissingFile(@TempDir Path dir) {
        assertFalse(LogFileFormatDetector.hasMagic(dir.resolve("does-not-exist"), 0, 0));
    }
}

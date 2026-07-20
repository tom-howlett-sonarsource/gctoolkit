// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileFormatsTest {

    @Test
    void detectsDirectory(@TempDir Path tmp) {
        assertEquals(LogFileFormat.DIRECTORY, LogFileFormats.detect(tmp));
    }

    @Test
    void detectsPlainText(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("gc.log");
        Files.writeString(plain, "hello\nworld\n");
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormats.detect(plain));
    }

    @Test
    void detectsGZip(@TempDir Path tmp) throws IOException {
        Path gz = tmp.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("gc entries".getBytes());
        }
        assertEquals(LogFileFormat.GZIP, LogFileFormats.detect(gz));
    }

    @Test
    void detectsZip(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("gc entries".getBytes());
            out.closeEntry();
        }
        assertEquals(LogFileFormat.ZIP, LogFileFormats.detect(zip));
    }

    @Test
    void magicMismatchReturnsPlainForShortFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("empty.log");
        Files.write(file, new byte[]{0x00});
        assertEquals(LogFileFormat.PLAINTEXT, LogFileFormats.detect(file));
    }

    @Test
    void hasMagicMatchesLeadingBytes(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("magic.bin");
        Files.write(file, new byte[]{0x1F, (byte) 0x8B, 0x08});
        assertTrue(LogFileFormats.hasMagic(file, LogFileFormats.GZIP_MAGIC1, LogFileFormats.GZIP_MAGIC2));
        assertFalse(LogFileFormats.hasMagic(file, LogFileFormats.ZIP_MAGIC1, LogFileFormats.ZIP_MAGIC2));
    }

    @Test
    void hasMagicReturnsFalseForMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist");
        assertFalse(LogFileFormats.hasMagic(missing, 0x1F, 0x8B));
    }

    @Test
    void detectRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> LogFileFormats.detect(null));
    }

    @Test
    void hasMagicRejectsNullPath() {
        assertThrows(NullPointerException.class, () -> LogFileFormats.hasMagic(null, 0, 0));
    }
}

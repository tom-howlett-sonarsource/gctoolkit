// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.util;

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

class GCLogSourcesTest {

    @Test
    void detectsPlainTextFile(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("gc.log");
        Files.writeString(plain, "hello");
        assertEquals(GCLogSourceFormat.PLAINTEXT, GCLogSources.detect(plain));
    }

    @Test
    void detectsGZipFile(@TempDir Path dir) throws IOException {
        Path gz = dir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("gc line".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(GCLogSourceFormat.GZIP, GCLogSources.detect(gz));
    }

    @Test
    void detectsZipFile(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve("gc.log.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write("gc line".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        assertEquals(GCLogSourceFormat.ZIP, GCLogSources.detect(zip));
    }

    @Test
    void detectsDirectory(@TempDir Path dir) {
        assertEquals(GCLogSourceFormat.DIRECTORY, GCLogSources.detect(dir));
    }

    @Test
    void detectsUnknownForNullPath() {
        assertEquals(GCLogSourceFormat.UNKNOWN, GCLogSources.detect(null));
    }

    @Test
    void matchesMagicReturnsFalseForMissingFile(@TempDir Path dir) {
        assertFalse(GCLogSources.matchesMagic(dir.resolve("missing.log"),
                GCLogSources.GZIP_MAGIC1, GCLogSources.GZIP_MAGIC2));
    }

    @Test
    void matchesMagicReturnsTrueOnMatch(@TempDir Path dir) throws IOException {
        Path gz = dir.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            out.write("body".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(GCLogSources.matchesMagic(gz,
                GCLogSources.GZIP_MAGIC1, GCLogSources.GZIP_MAGIC2));
    }

    @Test
    void byteSizeReturnsFileSize(@TempDir Path dir) throws IOException {
        Path plain = dir.resolve("gc.log");
        Files.writeString(plain, "12345");
        assertEquals(5L, GCLogSources.byteSize(plain));
    }
}

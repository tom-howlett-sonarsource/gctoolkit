// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedOutputStream;
import java.io.IOException;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");

    @Test
    void discoversDirectoryFormat(@TempDir Path tmp) {
        assertEquals(LogFileFormat.DIRECTORY, GCLogSources.discoverFormat(tmp));
    }

    @Test
    void discoversPlainTextAndStreams(@TempDir Path tmp) throws IOException {
        Path plain = tmp.resolve("plain.log");
        Files.write(plain, LINES);

        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.discoverFormat(plain));
        try (Stream<String> stream = GCLogSources.openPlain(plain)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
        assertTrue(GCLogSources.sizeInBytes(plain) > 0);
    }

    @Test
    void discoversGZipAndStreams(@TempDir Path tmp) throws IOException {
        Path gz = tmp.resolve("log.gz");
        try (GZIPOutputStream out = new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(gz)))) {
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
        }

        assertEquals(LogFileFormat.GZIP, GCLogSources.discoverFormat(gz));
        assertTrue(GCLogSources.magic(gz, GCLogSources.GZIP_MAGIC1, GCLogSources.GZIP_MAGIC2));
        assertFalse(GCLogSources.magic(gz, GCLogSources.ZIP_MAGIC1, GCLogSources.ZIP_MAGIC2));

        try (Stream<String> stream = GCLogSources.openGZip(gz)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void discoversZipAndStreamsFirstEntry(@TempDir Path tmp) throws IOException {
        Path zip = tmp.resolve("log.zip");
        try (ZipOutputStream out = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zip)))) {
            out.putNextEntry(new ZipEntry("subdir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write(String.join("\n", LINES).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        assertEquals(LogFileFormat.ZIP, GCLogSources.discoverFormat(zip));
        try (Stream<String> stream = GCLogSources.openZip(zip)) {
            assertEquals(LINES, stream.collect(Collectors.toList()));
        }
    }

    @Test
    void sizeInBytesReturnsNegativeOneForMissingFile(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.log");
        assertEquals(-1L, GCLogSources.sizeInBytes(missing));
    }

    @Test
    void magicReturnsFalseWhenFileMissing(@TempDir Path tmp) {
        Path missing = tmp.resolve("nope.log");
        assertFalse(GCLogSources.magic(missing, 0, 0));
    }
}

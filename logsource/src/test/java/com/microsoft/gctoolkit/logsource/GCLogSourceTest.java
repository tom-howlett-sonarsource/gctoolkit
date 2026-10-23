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
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {
    private static final String LINE = "[0.001s][info][gc] test";
    private static final String CONTENT = LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormats() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(writePlain()));
        assertEquals(LogFileFormat.GZIP, GCLogSource.discoverFormat(writeGzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSource.discoverFormat(writeZip()));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSource.discoverFormat(directory));
    }

    @Test
    void sizesLeadingMagicBytes() throws IOException {
        Path gzip = writeGzip();
        assertTrue(GCLogSource.hasMagicBytes(gzip, GCLogSource.GZIP_MAGIC1, GCLogSource.GZIP_MAGIC2));
        assertFalse(GCLogSource.hasMagicBytes(gzip, GCLogSource.ZIP_MAGIC1, GCLogSource.ZIP_MAGIC2));
    }

    @Test
    void opensPlainZipAndGzipStreams() throws IOException {
        assertContainsLine(GCLogSource.openPlainText(writePlain()));
        assertContainsLine(GCLogSource.openGZip(writeGzip()));
        assertContainsLine(GCLogSource.openZip(writeZip()));
    }

    private void assertContainsLine(java.util.stream.Stream<String> stream) {
        try (stream) {
            List<String> lines = stream.map(String::trim).collect(Collectors.toList());
            assertTrue(lines.contains(LINE));
        }
    }

    private Path writePlain() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

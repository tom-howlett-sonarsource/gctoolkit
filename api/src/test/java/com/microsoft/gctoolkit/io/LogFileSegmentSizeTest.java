// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileSegmentSizeTest {

    @TempDir
    Path directory;

    @Test
    void gcLogFileSegmentReturnsFileSizeInBytes() throws Exception {
        byte[] content = "gc log content\n".getBytes(StandardCharsets.UTF_8);
        Path file = directory.resolve("gc.log");
        Files.write(file, content);

        assertEquals(content.length, new GCLogFileSegment(file).getSizeInBytes());
    }

    @Test
    void gcLogFileZipSegmentReturnsUncompressedEntrySize() throws Exception {
        byte[] content = "gc log content\n".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(content);
            output.closeEntry();
        }

        assertEquals(content.length, new GCLogFileZipSegment(archive, "gc.log").getSizeInBytes());
    }

    @Test
    void gcLogFileZipSegmentReturnsZeroForMissingEntry() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("gc.log"));
            output.closeEntry();
        }

        assertEquals(0L, new GCLogFileZipSegment(archive, "does-not-exist.log").getSizeInBytes());
    }
}

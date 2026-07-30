// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void totalByteSizeForIndividualMemberOfRotatingSet() throws IOException {
        Path current = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        Path previous = new TestLogFile("G1-80-16gbps2.log.0").getFile().toPath();
        long expected = Files.size(current) + Files.size(previous);

        assertEquals(expected, new RotatingLogFileMetadata(previous).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroForEmptyDirectory() throws IOException {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroForZipWithOnlyDirectoryEntries() throws Exception {
        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroForCorruptZip() throws IOException {
        Path corrupt = directory.resolve("corrupt.zip");
        Files.write(corrupt, new byte[]{(byte) 0x50, (byte) 0x4b, 0, 0, 1, 2, 3});
        assertEquals(0L, new RotatingLogFileMetadata(corrupt).getTotalByteSize());
    }

    @Test
    void totalByteSizeIsZeroForUnsupportedFormat() throws IOException {
        Path gzip = directory.resolve("gc.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzip))) {
            output.write("gc log content".getBytes());
        }
        assertEquals(0L, new RotatingLogFileMetadata(gzip).getTotalByteSize());
    }
}

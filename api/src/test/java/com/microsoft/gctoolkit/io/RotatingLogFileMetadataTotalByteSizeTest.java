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

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromAMemberOfTheSet() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Path member = directory.resolve("gc.log");
        Files.write(member, current);

        assertEquals(first.length + second.length + current.length,
                new RotatingLogFileMetadata(member).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipWithOnlyDirectoryEntries() throws Exception {
        Path archive = directory.resolve("dirs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

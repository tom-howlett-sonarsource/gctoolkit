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

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsRotatingSetWhenConstructedFromOneMember() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path firstSegment = Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        assertEquals(first.length + second.length + current.length,
                new RotatingLogFileMetadata(firstSegment).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoFiles() throws Exception {
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void ignoresDirectoryEntriesInZip() throws Exception {
        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

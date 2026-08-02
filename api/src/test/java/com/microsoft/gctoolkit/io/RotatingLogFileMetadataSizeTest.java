// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromAnySetMember() throws Exception {
        byte[] first = "first segment\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second segment\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active segment\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Path firstMember = Files.write(logs.resolve("gc.log.0"), first);
        Files.write(logs.resolve("gc.log.1"), second);
        Path activeMember = Files.write(logs.resolve("gc.log"), active);
        long expectedSize = first.length + second.length + active.length;

        assertEquals(expectedSize,
                new RotatingLogFileMetadata(firstMember).getTotalByteSize());
        assertEquals(expectedSize,
                new RotatingLogFileMetadata(activeMember).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty-logs"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());

        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(archive))) {
            // Create an empty ZIP archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

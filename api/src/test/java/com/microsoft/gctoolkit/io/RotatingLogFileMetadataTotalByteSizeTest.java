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
    void sumsWhenConstructedFromMemberOfRotatingSet() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second log entry\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        // Construct from an individual member of the rotating-file set.
        RotatingLogFileMetadata metadata =
                new RotatingLogFileMetadata(directory.resolve("gc.log.0"));
        assertEquals(first.length + second.length + current.length,
                metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void zipSumsUncompressedSizeIgnoringDirectoryEntries() throws Exception {
        byte[] first = "aaaaaaaaaa\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "bbbbbbbbbbbbbbb\n".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write(first);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(current);
            output.closeEntry();
        }
        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void isIdempotentAcrossRepeatedCalls() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), first);
        Files.write(logs.resolve("gc.log"), current);
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);

        long expected = first.length + current.length;
        assertEquals(expected, metadata.getTotalByteSize());
        assertEquals(expected, metadata.getTotalByteSize());
    }
}

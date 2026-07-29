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
    void sumsSegmentsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), first);
        Path currentSegment = Files.write(logs.resolve("gc.log"), current);

        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(currentSegment).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        Path emptyDir = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDir).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipWithNoFileEntries() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

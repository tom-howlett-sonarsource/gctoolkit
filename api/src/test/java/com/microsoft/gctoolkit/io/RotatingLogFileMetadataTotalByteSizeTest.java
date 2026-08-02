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
    void sumsAllSegmentsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] previous = "previous".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current".getBytes(StandardCharsets.UTF_8);
        Path previousLog = Files.write(directory.resolve("gc.log.0"), previous);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("unrelated.log"), new byte[100]);

        assertEquals(previous.length + current.length,
                new RotatingLogFileMetadata(previousLog).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenNoEligibleEntriesExist() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

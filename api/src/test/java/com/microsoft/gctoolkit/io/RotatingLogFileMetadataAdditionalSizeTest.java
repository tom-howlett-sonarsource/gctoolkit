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

class RotatingLogFileMetadataAdditionalSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsAllSegmentsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] first = "[0.001s] first\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "[0.002s] current\n".getBytes(StandardCharsets.UTF_8);
        Path firstSegment = Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log"), current);

        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(firstSegment).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(emptyZip).getTotalByteSize());
    }
}

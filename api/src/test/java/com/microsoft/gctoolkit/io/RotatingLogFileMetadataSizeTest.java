// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversAllSegmentsFromAnIndividualMember() throws Exception {
        Path numberedSegment = Files.write(directory.resolve("gc.log.0"), new byte[3]);
        Files.write(directory.resolve("gc.log"), new byte[5]);
        Files.write(directory.resolve("other.log"), new byte[7]);
        Files.createDirectory(directory.resolve("gc.log.1"));

        assertEquals(8L, new RotatingLogFileMetadata(numberedSegment).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenThereAreNoEligibleEntries() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyArchive = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyArchive))) {
            // Create an empty ZIP file.
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyArchive).getTotalByteSize());
    }
}

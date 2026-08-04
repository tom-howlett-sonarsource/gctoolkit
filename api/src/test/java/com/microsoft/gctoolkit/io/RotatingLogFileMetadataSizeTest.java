// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsRotatingSetWhenConstructedFromAnIndividualMember() throws Exception {
        Path first = Files.write(directory.resolve("gc.log.0"), new byte[] { 1, 2 });
        Files.write(directory.resolve("gc.log.1"), new byte[] { 3, 4, 5 });
        Files.write(directory.resolve("gc.log"), new byte[] { 6, 7, 8, 9 });
        Files.write(directory.resolve("unrelated.log"), new byte[] { 10, 11, 12, 13, 14 });

        assertEquals(9L, new RotatingLogFileMetadata(first).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenThereAreNoEligibleEntries() throws Exception {
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

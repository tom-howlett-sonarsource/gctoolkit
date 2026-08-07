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

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversSetFromAnIndividualMember() throws Exception {
        Path current = Files.write(directory.resolve("gc.log"), new byte[11]);
        Path rotated = Files.write(directory.resolve("gc.log.0"), new byte[7]);
        Files.write(directory.resolve("other.log"), new byte[13]);

        assertEquals(18L, new RotatingLogFileMetadata(rotated).getTotalByteSize());
        assertEquals(18L, new RotatingLogFileMetadata(current).getTotalByteSize());
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

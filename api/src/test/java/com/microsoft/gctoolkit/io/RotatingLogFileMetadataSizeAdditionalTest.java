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

class RotatingLogFileMetadataSizeAdditionalTest {

    @TempDir
    Path directory;

    @Test
    void discoversTheCompleteSetFromAnIndividualMember() throws Exception {
        Path current = Files.write(directory.resolve("gc.log"), new byte[7]);
        Path rotated = Files.write(directory.resolve("gc.log.0"), new byte[11]);
        Files.write(directory.resolve("other.log"), new byte[13]);

        assertEquals(18L, new RotatingLogFileMetadata(current).getTotalByteSize());
        assertEquals(18L, new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenThereAreNoEligibleEntries() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.createDirectory(logs.resolve("nested"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());

        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

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
    void discoversSetFromAnIndividualMember() throws Exception {
        byte[] rotated = new byte[7];
        byte[] active = new byte[11];
        Path member = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("other.log"), new byte[13]);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(member).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoEligibleEntries() throws Exception {
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenZipHasNoEligibleEntries() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

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
    void sumsRotatingSetWhenConstructedFromIndividualMember() throws Exception {
        byte[] rotatedContent = new byte[] { 1, 2, 3 };
        byte[] activeContent = new byte[] { 4, 5, 6, 7, 8 };
        Path rotated = Files.write(directory.resolve("gc.log.0"), rotatedContent);
        Files.write(directory.resolve("gc.log"), activeContent);
        Files.write(directory.resolve("unrelated.log"), new byte[] { 9, 10 });

        assertEquals(rotatedContent.length + activeContent.length,
                new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoEligibleEntries() throws Exception {
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenZipHasOnlyDirectories() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

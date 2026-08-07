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
        Path current = Files.write(directory.resolve("gc.log"), new byte[7]);
        Path rotated = Files.write(directory.resolve("gc.log.0"), new byte[11]);

        assertEquals(Files.size(current) + Files.size(rotated),
                new RotatingLogFileMetadata(rotated).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoFiles() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));

        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());
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

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
    void discoversSetFromIndividualRotatingMember() throws Exception {
        byte[] rotated = "rotated".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active".getBytes(StandardCharsets.UTF_8);
        Path member = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("unrelated.log"), new byte[13]);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(member).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoFiles() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));

        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenZipContainsOnlyDirectories() throws Exception {
        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

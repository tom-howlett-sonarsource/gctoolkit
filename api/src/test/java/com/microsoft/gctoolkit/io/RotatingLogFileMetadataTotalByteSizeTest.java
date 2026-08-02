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
    void sumsSetWhenConstructedFromRotatingMember() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "second log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path firstPath = Files.write(directory.resolve("gc.log.0"), first);
        Path secondPath = Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("unrelated.log"), new byte[31]);

        assertEquals(first.length + second.length + current.length,
                new RotatingLogFileMetadata(firstPath).getTotalByteSize());
        assertEquals(first.length + second.length + current.length,
                new RotatingLogFileMetadata(secondPath).getTotalByteSize());
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

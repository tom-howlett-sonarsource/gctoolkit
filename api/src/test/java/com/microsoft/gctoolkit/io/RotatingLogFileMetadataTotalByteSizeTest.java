// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSiblingsWhenConstructedFromAnIndividualMember() throws IOException {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        long expected = Files.size(path) + Files.size(path.getParent().resolve("G1-80-16gbps2.log.0"));
        assertEquals(expected, new RotatingLogFileMetadata(path).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipWithOnlyDirectoryEntries() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

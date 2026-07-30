// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataGetTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void constructionFromIndividualMemberSumsAllSegments() throws Exception {
        Path current = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        Path rotated = current.resolveSibling("G1-80-16gbps2.log.0");

        long expected = Files.size(current) + Files.size(rotated);

        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
    }

    @Test
    void directoryWithSingleSegmentReturnsItsSize() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        byte[] content = "only log\n".getBytes(StandardCharsets.UTF_8);
        Files.write(logs.resolve("gc.log"), content);

        assertEquals(content.length, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void emptyDirectoryReturnsZero() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty"));

        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoryEntryReturnsZero() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void unsupportedFormatReturnsZero() throws Exception {
        Path gzip = directory.resolve("gc.log.gz");
        Files.write(gzip, new byte[]{(byte) 0x1F, (byte) 0x8B, 0, 0});

        assertEquals(0L, new RotatingLogFileMetadata(gzip).getTotalByteSize());
    }
}

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

class RotatingLogFileMetadataByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversAllSegmentsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] archived = "[1.0s] archived\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "[2.0s] current\n".getBytes(StandardCharsets.UTF_8);
        Path archivedLog = Files.write(directory.resolve("gc.log.0"), archived);
        Files.write(directory.resolve("gc.log"), current);

        assertEquals(archived.length + current.length,
                new RotatingLogFileMetadata(archivedLog).getTotalByteSize());
    }

    @Test
    void returnsZeroForAnEmptyDirectory() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void ignoresZipDirectoriesAndUsesUncompressedEntrySizes() throws Exception {
        byte[] content = new byte[4096];
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(content);
            output.closeEntry();
        }

        assertEquals(content.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

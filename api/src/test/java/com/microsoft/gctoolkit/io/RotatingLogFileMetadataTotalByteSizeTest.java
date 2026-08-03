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
    void discoversRotatingSetFromIndividualMember() throws Exception {
        byte[] archived = "archived log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path archivedLog = Files.write(directory.resolve("gc.log.0"), archived);
        Files.write(directory.resolve("gc.log"), active);

        assertEquals(archived.length + active.length,
                new RotatingLogFileMetadata(archivedLog).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoRegularFiles() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.createDirectory(logs.resolve("nested"));

        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenZipHasNoFileEntries() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

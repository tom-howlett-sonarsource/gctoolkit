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
    void sumsSiblingsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), first);
        Path currentLog = Files.write(logs.resolve("gc.log"), current);

        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(currentLog).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryHasNoSegments() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty-logs"));

        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenZipHasNoNonDirectoryEntries() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void doesNotDisturbExistingDiscoveryAndOrderingBehavior() throws Exception {
        byte[] first = "first log\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current log\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), first);
        Files.write(logs.resolve("gc.log"), current);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);
        long totalBeforeLogFiles = metadata.getTotalByteSize();
        metadata.getNumberOfFiles();
        long totalAfterLogFiles = metadata.getTotalByteSize();

        assertEquals(first.length + current.length, totalBeforeLogFiles);
        assertEquals(totalBeforeLogFiles, totalAfterLogFiles);
    }
}

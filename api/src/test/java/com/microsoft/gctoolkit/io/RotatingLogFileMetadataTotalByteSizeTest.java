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

    private static final byte[] ROTATED = "rotated log\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURRENT = "current log\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void sumsSetWhenConstructedFromASingleMember() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log"), CURRENT);

        long expected = ROTATED.length + CURRENT.length;
        assertEquals(expected, new RotatingLogFileMetadata(logs.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(logs.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void ignoresFilesOutsideOfTheRotatingSet() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log"), CURRENT);
        Files.write(logs.resolve("other.log"), ROTATED);

        assertEquals(CURRENT.length, new RotatingLogFileMetadata(logs.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void skipsNestedDirectoriesOfADirectory() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log"), CURRENT);
        Files.createDirectory(logs.resolve("nested"));

        assertEquals(CURRENT.length, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroForAnEmptyDirectory() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));

        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroForAZipHoldingOnlyDirectoryEntries() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void sumsUncompressedSizesOfHighlyCompressibleZipEntries() throws Exception {
        byte[] compressible = new byte[64 * 1024];
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write(compressible);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CURRENT);
            output.closeEntry();
        }

        assertEquals(compressible.length + CURRENT.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void discoveryAndOrderingAreUnchangedByAskingForTheSize() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write(ROTATED);
            output.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);
        assertEquals(ROTATED.length, metadata.getTotalByteSize());
        assertEquals(1, metadata.getNumberOfFiles());
        assertEquals("gc.log.0", metadata.logFiles().findFirst().get().getSegmentName());
    }
}

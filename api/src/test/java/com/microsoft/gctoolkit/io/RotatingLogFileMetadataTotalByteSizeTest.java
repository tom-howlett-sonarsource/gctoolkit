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

    private static final byte[] ROTATED = "rotated log\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURRENT = "current log entry\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void emptyDirectoryHasNoBytes() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void directoryIgnoresNestedDirectories() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.createDirectory(logs.resolve("nested"));
        Files.write(logs.resolve("gc.log"), CURRENT);
        assertEquals(CURRENT.length, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void memberOfRotatingSetSumsWholeSet() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log.1"), ROTATED);
        Path current = Files.write(logs.resolve("gc.log"), CURRENT);
        Files.write(logs.resolve("other.log"), CURRENT);

        long expected = (2L * ROTATED.length) + CURRENT.length;
        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
        assertEquals(expected,
                new RotatingLogFileMetadata(logs.resolve("gc.log.1")).getTotalByteSize());
    }

    @Test
    void preUnifiedCurrentSuffixSumsWholeSet() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Path current = Files.write(logs.resolve("gc.log.1.current"), CURRENT);
        assertEquals(ROTATED.length + CURRENT.length,
                new RotatingLogFileMetadata(current).getTotalByteSize());
    }

    @Test
    void zipWithoutEligibleEntriesHasNoBytes() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void zipSumsUncompressedSizesOfNestedEntries() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, "logs/", new byte[0]);
            write(output, "logs/gc.log.0", ROTATED);
            write(output, "logs/gc.log", CURRENT);
        }
        assertEquals(ROTATED.length + CURRENT.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeDoesNotDisturbSegmentDiscovery() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log"), CURRENT);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);
        int filesBefore = metadata.getNumberOfFiles();
        metadata.getTotalByteSize();
        assertEquals(filesBefore, metadata.getNumberOfFiles());
        assertEquals(filesBefore, metadata.logFiles().count());
    }

    private static void write(ZipOutputStream output, String name, byte[] content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

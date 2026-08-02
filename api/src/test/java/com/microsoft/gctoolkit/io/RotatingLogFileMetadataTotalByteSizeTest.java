// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    private static final byte[] ROTATED = "rotated log line\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURRENT = "current log line\n".getBytes(StandardCharsets.UTF_8);

    @Test
    void sumsAllSegmentsWhenConstructedFromAMemberOfTheSet() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log"), CURRENT);
        long expected = ROTATED.length + CURRENT.length;

        assertEquals(expected, new RotatingLogFileMetadata(logs.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(logs.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void ignoresFilesThatAreNotPartOfTheSet() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log"), CURRENT);
        Files.write(logs.resolve("other.log"), ROTATED);

        assertEquals(CURRENT.length, new RotatingLogFileMetadata(logs.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void countsNestedZipEntriesAndSkipsDirectoryEntries() throws Exception {
        Path archive = directory.resolve("nested.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log.0"));
            output.write(ROTATED);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CURRENT);
            output.closeEntry();
        }

        assertEquals(ROTATED.length + CURRENT.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForAnEmptyDirectory() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void returnsZeroForAZipHoldingOnlyDirectoryEntries() throws Exception {
        Path archive = directory.resolve("dirs-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void doesNotDisturbSegmentDiscovery() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log"), CURRENT);

        // Sizing must not perturb the segments that discovery and ordering hand back.
        int expectedSegments = new RotatingLogFileMetadata(logs).getNumberOfFiles();
        List<String> expectedOrder = new RotatingLogFileMetadata(logs).logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);
        assertEquals(ROTATED.length + CURRENT.length, metadata.getTotalByteSize());
        assertEquals(expectedSegments, metadata.getNumberOfFiles());
        assertEquals(expectedOrder, metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList()));
        assertEquals(ROTATED.length + CURRENT.length, metadata.getTotalByteSize());
    }
}

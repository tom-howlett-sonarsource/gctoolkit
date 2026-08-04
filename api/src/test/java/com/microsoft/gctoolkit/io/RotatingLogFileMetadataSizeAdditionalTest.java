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

class RotatingLogFileMetadataSizeAdditionalTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromRotatedMember() throws Exception {
        byte[] rotated = "rotated log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path rotatedPath = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(rotatedPath);
        List<String> orderBefore = metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());

        assertEquals(rotated.length + active.length, metadata.getTotalByteSize());
        assertEquals(orderBefore, metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList()));
    }

    @Test
    void returnsZeroForEmptyDirectory() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void sumsEveryNonDirectoryZipEntryUsingUncompressedSize() throws Exception {
        byte[] first = "first".repeat(100).getBytes(StandardCharsets.UTF_8);
        byte[] second = "second".repeat(100).getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            addEntry(output, "logs/", new byte[0]);
            addEntry(output, "logs/first.log", first);
            addEntry(output, "notes.txt", second);
        }

        assertEquals(first.length + second.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForZipContainingOnlyDirectories() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            addEntry(output, "logs/", new byte[0]);
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    private static void addEntry(ZipOutputStream output, String name, byte[] content)
            throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

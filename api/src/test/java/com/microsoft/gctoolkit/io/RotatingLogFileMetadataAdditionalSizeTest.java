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

class RotatingLogFileMetadataAdditionalSizeTest {

    @TempDir
    Path directory;

    @Test
    void memberInputIncludesEverySegmentWithoutChangingOrdering() throws Exception {
        byte[] first = "[0.001s][info][gc] first\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "[0.002s][info][gc] second\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "[0.003s][info][gc] current\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Path secondPath = Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(secondPath);
        List<String> orderBeforeSizing = metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());

        assertEquals(first.length + second.length + current.length,
                metadata.getTotalByteSize());
        assertEquals(orderBeforeSizing, metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList()));
    }

    @Test
    void returnsZeroWhenDirectoryAndZipHaveNoEligibleEntries() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        Files.createDirectory(empty.resolve("nested"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());

        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

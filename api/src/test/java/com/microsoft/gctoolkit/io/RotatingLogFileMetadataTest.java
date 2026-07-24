// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class RotatingLogFileMetadataTest {

    @Test
    void directoryInputSumsFileSizes() {
        Path path = new TestLogFile("rotating_directory").getFile().toPath();
        try {
            RotatingLogFileMetadata metaData = new RotatingLogFileMetadata(path);
            long totalByteSize = metaData.getTotalByteSize();
            long expectedSize = Files.list(path)
                    .filter(Files::isRegularFile)
                    .mapToLong(f -> {
                        try { return Files.size(f); } catch (IOException e) { return 0L; }
                    })
                    .sum();
            assertEquals(expectedSize, totalByteSize);
            assertTrue(totalByteSize > 0L);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void individualRotatingMemberSumsSiblings() {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        try {
            RotatingLogFileMetadata metaData = new RotatingLogFileMetadata(path);
            long totalByteSize = metaData.getTotalByteSize();
            assertTrue(totalByteSize > 0L);
            assertEquals(metaData.getNumberOfFiles(), 2);
            long expectedSize = Files.list(path.getParent())
                    .filter(f -> f.getFileName().toString().startsWith("G1-80-16gbps2"))
                    .mapToLong(f -> {
                        try { return Files.size(f); } catch (IOException e) { return 0L; }
                    })
                    .sum();
            assertEquals(expectedSize, totalByteSize);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void zipInputSumsUncompressedSizes() {
        Path path = new TestLogFile("rotating.zip").getFile().toPath();
        try {
            RotatingLogFileMetadata metaData = new RotatingLogFileMetadata(path);
            long totalByteSize = metaData.getTotalByteSize();
            assertEquals(4366967L, totalByteSize);
            assertTrue(totalByteSize > 0L);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void zipWithDirectoryEntriesSumsOnlyFiles() {
        Path path = new TestLogFile("rotating_directory.zip").getFile().toPath();
        try {
            RotatingLogFileMetadata metaData = new RotatingLogFileMetadata(path);
            long totalByteSize = metaData.getTotalByteSize();
            assertEquals(4367597L, totalByteSize);
            assertTrue(totalByteSize > 0L);
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void getTotalByteSizePreservesSegmentDiscovery() {
        Path path = new TestLogFile("rotating.zip").getFile().toPath();
        try {
            RotatingLogFileMetadata metaData = new RotatingLogFileMetadata(path);
            long firstCall = metaData.getTotalByteSize();
            int numberOfFiles = metaData.getNumberOfFiles();
            long secondCall = metaData.getTotalByteSize();
            assertEquals(firstCall, secondCall);
            assertEquals(2, numberOfFiles);
        } catch (IOException e) {
            fail(e);
        }
    }
}

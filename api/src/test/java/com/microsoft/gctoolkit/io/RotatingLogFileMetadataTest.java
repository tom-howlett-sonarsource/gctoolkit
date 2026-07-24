// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingLogFileMetadataTest {

    private static byte[] buildSegmentContent(double startSeconds, double endSeconds, int paddingLines) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%.3fs][info][gc] GC start%n", startSeconds));
        for (int i = 0; i < paddingLines; i++) {
            double t = startSeconds + (endSeconds - startSeconds) * i / Math.max(paddingLines, 1);
            sb.append(String.format("[%.3fs][info][gc] padding line %d%n", t, i));
        }
        sb.append(String.format("[%.3fs][info][gc] GC end%n", endSeconds));
        return sb.toString().getBytes();
    }

    @Test
    void getTotalByteSizeFromDirectory(@TempDir Path tempDir) throws IOException {
        byte[] content1 = buildSegmentContent(10.0, 20.0, 5);
        byte[] content2 = buildSegmentContent(0.0, 9.0, 3);
        Files.write(tempDir.resolve("gc.log"), content1);
        Files.write(tempDir.resolve("gc.log.0"), content2);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);

        long expected = content1.length + content2.length;
        assertEquals(expected, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeFromIndividualMember(@TempDir Path tempDir) throws IOException {
        byte[] content1 = buildSegmentContent(10.0, 20.0, 4);
        byte[] content2 = buildSegmentContent(0.0, 9.0, 2);
        Path active = tempDir.resolve("gc.log");
        Path rotated = tempDir.resolve("gc.log.0");
        Files.write(active, content1);
        Files.write(rotated, content2);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(active);

        long expected = content1.length + content2.length;
        assertEquals(expected, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeFromZip(@TempDir Path tempDir) throws IOException {
        byte[] content1 = buildSegmentContent(0.0, 9.0, 3);
        byte[] content2 = buildSegmentContent(10.0, 20.0, 5);
        Path zipPath = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry("gc.log.0"));
            zos.write(content1);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("gc.log.1.current"));
            zos.write(content2);
            zos.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath);

        long expected = content1.length + content2.length;
        assertEquals(expected, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeFromZipExcludesDirectories(@TempDir Path tempDir) throws IOException {
        byte[] content = buildSegmentContent(0.0, 5.0, 2);
        Path zipPath = tempDir.resolve("gc.log.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry("subdir/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("subdir/gc.log"));
            zos.write(content);
            zos.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath);

        assertEquals(content.length, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeReturnsZeroForEmptyZip(@TempDir Path tempDir) throws IOException {
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            zos.putNextEntry(new ZipEntry("onlydir/"));
            zos.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeReturnsZeroForEmptyDirectory(@TempDir Path tempDir) throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizePreservesSegmentDiscovery(@TempDir Path tempDir) throws IOException {
        byte[] content1 = buildSegmentContent(10.0, 20.0, 3);
        byte[] content2 = buildSegmentContent(0.0, 9.0, 2);
        Files.write(tempDir.resolve("gc.log"), content1);
        Files.write(tempDir.resolve("gc.log.0"), content2);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);

        assertEquals(2, metadata.getNumberOfFiles());
        long expected = content1.length + content2.length;
        assertEquals(expected, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeIsConsistentAcrossMultipleCalls(@TempDir Path tempDir) throws IOException {
        byte[] content = buildSegmentContent(0.0, 5.0, 3);
        Files.write(tempDir.resolve("gc.log"), content);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);

        long first = metadata.getTotalByteSize();
        long second = metadata.getTotalByteSize();
        assertEquals(first, second);
        assertTrue(first > 0);
    }
}

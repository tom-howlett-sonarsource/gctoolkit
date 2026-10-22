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

/**
 * Supplementary coverage for {@link RotatingLogFileMetadata#getTotalByteSize()}.
 * The byte-for-byte acceptance test lives in VisibleRotatingLogFileMetadataSizeTest.
 */
class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void constructedFromIndividualMemberSumsWholeSet() throws Exception {
        byte[] first = "aaaa\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "bbbbbb\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "cc\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        // Construct from a single (non-current) member of the rotating set.
        RotatingLogFileMetadata metadata =
                new RotatingLogFileMetadata(directory.resolve("gc.log.0"));

        assertEquals(first.length + second.length + current.length,
                metadata.getTotalByteSize());
    }

    @Test
    void emptyDirectoryReturnsZero() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void zipIgnoresDirectoryEntriesAndSumsUncompressedSizes() throws Exception {
        byte[] a = "hello world\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "goodbye\n".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("nested/gc.log.0"));
            output.write(a);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("nested/gc.log"));
            output.write(b);
            output.closeEntry();
        }
        assertEquals(a.length + b.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void emptyZipReturnsZero() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("onlyadir/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

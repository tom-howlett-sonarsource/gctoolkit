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
    void discoversWholeSetFromIndividualRotatedMember() throws Exception {
        byte[] first = "aaaa\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "bbbbbb\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "cc\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log.1"), second);
        Files.write(directory.resolve("gc.log"), current);

        long expected = first.length + second.length + current.length;

        assertEquals(expected,
                new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(expected,
                new RotatingLogFileMetadata(directory.resolve("gc.log.1")).getTotalByteSize());
        assertEquals(expected,
                new RotatingLogFileMetadata(directory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void discoversWholeSetFromCurrentSuffixMember() throws Exception {
        byte[] rotated = "old\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "now\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log.1.current"), current);

        assertEquals(rotated.length + current.length,
                new RotatingLogFileMetadata(directory.resolve("gc.log.1.current")).getTotalByteSize());
    }

    @Test
    void unrelatedSiblingsAreIgnoredWhenStartingFromMember() throws Exception {
        byte[] first = "one\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "two\n".getBytes(StandardCharsets.UTF_8);
        byte[] unrelated = "noise\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("other.log"), unrelated);

        assertEquals(first.length + current.length,
                new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize());
    }

    @Test
    void emptyDirectoryReturnsZero() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoryEntriesReturnsZero() throws Exception {
        Path archive = directory.resolve("dirs-only.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("logs/nested/"));
            out.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void zipSumsUncompressedSizesAcrossManyEntries() throws Exception {
        byte[] a = "alpha-payload\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "beta\n".getBytes(StandardCharsets.UTF_8);
        byte[] c = new byte[1024];
        for (int i = 0; i < c.length; i++) c[i] = (byte) (i & 0x7F);

        Path archive = directory.resolve("many.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            out.putNextEntry(new ZipEntry("logs/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log"));
            out.write(a);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log.0"));
            out.write(b);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("gc.log.1"));
            out.write(c);
            out.closeEntry();
        }

        assertEquals(a.length + b.length + c.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

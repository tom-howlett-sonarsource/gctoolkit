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
 * Additional coverage for {@link RotatingLogFileMetadata#getTotalByteSize()} beyond the
 * supplied visible acceptance test.
 */
class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void constructedFromIndividualMemberSumsWholeSet() throws Exception {
        byte[] oldest = "oldest\n".getBytes(StandardCharsets.UTF_8);
        byte[] middle = "middle entry\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current entry here\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), oldest);
        Files.write(directory.resolve("gc.log.1"), middle);
        Files.write(directory.resolve("gc.log"), current);

        // Construct from a single member of the rotating-file set rather than the directory.
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory.resolve("gc.log.1"));
        assertEquals(oldest.length + middle.length + current.length, metadata.getTotalByteSize());
    }

    @Test
    void preUnifiedCurrentSuffixIsIncluded() throws Exception {
        byte[] rotated = "rotated\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "being written\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log.1.current"), current);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory.resolve("gc.log.1.current"));
        assertEquals(rotated.length + current.length, metadata.getTotalByteSize());
    }

    @Test
    void emptyDirectoryReturnsZero() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoryEntriesReturnsZero() throws Exception {
        Path archive = directory.resolve("dirs-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/nested/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void zipSumsEveryNonDirectoryEntry() throws Exception {
        byte[] a = "aaaa\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "bbbbbb\n".getBytes(StandardCharsets.UTF_8);
        byte[] c = "cc\n".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("many.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            addEntry(output, "gc.log.0", a);
            addEntry(output, "gc.log.1", b);
            addEntry(output, "gc.log", c);
        }
        assertEquals(a.length + b.length + c.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    private static void addEntry(ZipOutputStream output, String name, byte[] content) throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

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

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void discoversRotatingSetFromAnIndividualMember() throws Exception {
        byte[] rotated = "rotated\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active\n".getBytes(StandardCharsets.UTF_8);
        Path rotatedPath = Files.write(directory.resolve("gc.log.0"), rotated);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("unrelated.log"), new byte[17]);

        assertEquals(rotated.length + active.length,
                new RotatingLogFileMetadata(rotatedPath).getTotalByteSize());
    }

    @Test
    void returnsZeroForDirectoryWithoutFilesAndDirectoryOnlyZip() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        Files.createDirectories(empty.resolve("first/nested"));
        Files.createDirectory(empty.resolve("second"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());

        Path archive = directory.resolve("directories.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void countsEveryNonDirectoryZipEntryWithoutRequiringRotatingNames() throws Exception {
        byte[] first = new byte[11];
        byte[] second = new byte[23];
        Path archive = directory.resolve("entries.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            addEntry(output, "first.txt", first);
            addEntry(output, "nested/second.txt", second);
        }

        assertEquals(first.length + second.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    private static void addEntry(ZipOutputStream output, String name, byte[] content)
            throws Exception {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

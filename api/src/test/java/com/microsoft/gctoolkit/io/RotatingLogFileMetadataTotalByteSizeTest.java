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
    void discoversRotatingSetFromIndividualMember() throws Exception {
        byte[] first = "one\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "two two\n".getBytes(StandardCharsets.UTF_8);
        byte[] current = "current entry\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), first);
        Files.write(logs.resolve("gc.log.1"), second);
        Files.write(logs.resolve("gc.log"), current);

        long expected = first.length + second.length + current.length;
        assertEquals(expected,
                new RotatingLogFileMetadata(logs.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(expected,
                new RotatingLogFileMetadata(logs.resolve("gc.log")).getTotalByteSize());
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
    void zipSumsMultipleEntries() throws Exception {
        byte[] a = "aaaa\n".getBytes(StandardCharsets.UTF_8);
        byte[] b = "bbbbbb\n".getBytes(StandardCharsets.UTF_8);
        byte[] c = "cc\n".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("many.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write(a);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.1"));
            output.write(b);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(c);
            output.closeEntry();
        }
        assertEquals(a.length + b.length + c.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void ignoresSubdirectoriesInsideLogDirectory() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        Files.write(logs.resolve("gc.log"), payload);
        Files.createDirectory(logs.resolve("subdir"));
        assertEquals(payload.length,
                new RotatingLogFileMetadata(logs).getTotalByteSize());
    }
}

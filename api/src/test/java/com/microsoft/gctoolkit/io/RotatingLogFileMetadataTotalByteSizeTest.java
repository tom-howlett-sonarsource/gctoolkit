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
    void plainTextMemberSumsSiblingsSharingRootPattern() throws Exception {
        byte[] rolled = "one\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "two\n".getBytes(StandardCharsets.UTF_8);
        byte[] unrelated = "noise\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), rolled);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("other.log"), unrelated);

        long total = new RotatingLogFileMetadata(directory.resolve("gc.log.0"))
                .getTotalByteSize();

        assertEquals(rolled.length + active.length, total);
    }

    @Test
    void emptyDirectoryReturnsZero() throws Exception {
        Path empty = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(empty).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoryEntryReturnsZero() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void directoryIgnoresSubdirectories() throws Exception {
        byte[] payload = "payload\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log"), payload);
        Files.createDirectory(logs.resolve("nested"));
        Files.write(logs.resolve("nested").resolve("inner.log"),
                "inner\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(payload.length,
                new RotatingLogFileMetadata(logs).getTotalByteSize());
    }
}

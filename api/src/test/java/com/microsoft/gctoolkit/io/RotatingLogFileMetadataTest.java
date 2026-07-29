// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTest {

    @TempDir
    Path directory;

    @Test
    void totalsSegmentsWhenConstructedFromRotatingSetMember() throws IOException {
        byte[] archived = "archived log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path archivedLog = Files.write(directory.resolve("gc.log.0"), archived);
        Files.write(directory.resolve("gc.log"), active);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archivedLog);

        assertEquals(archived.length + active.length, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroWhenDirectoryCannotBeRead() throws IOException {
        Path removedDirectory = Files.createDirectory(directory.resolve("removed"));
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(removedDirectory);
        Files.delete(removedDirectory);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroForZipContainingOnlyDirectories() throws IOException {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroForInvalidZip() throws IOException {
        Path archive = Files.write(directory.resolve("invalid.zip"), "PK invalid".getBytes(StandardCharsets.UTF_8));
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroForUnsupportedGzipInput() throws IOException {
        Path gzip = Files.write(directory.resolve("logs.gz"), new byte[]{0x1F, (byte) 0x8B});
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(gzip);

        assertEquals(0L, metadata.getTotalByteSize());
    }
}

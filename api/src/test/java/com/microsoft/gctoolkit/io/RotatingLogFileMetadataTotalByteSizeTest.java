// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsRotatingSetWhenConstructedFromMember() throws Exception {
        byte[] archived = { 1, 2, 3 };
        byte[] current = { 4, 5, 6, 7 };
        Path member = Files.write(directory.resolve("gc.log.0"), archived);
        Files.write(directory.resolve("gc.log"), current);
        Files.write(directory.resolve("other.log"), new byte[] { 8, 9 });
        Path relativeMember = Path.of("").toAbsolutePath().relativize(member.toAbsolutePath());

        assertEquals(archived.length + current.length,
                new RotatingLogFileMetadata(relativeMember).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenNoEligibleEntriesExist() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path archive = directory.resolve("directories-only.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

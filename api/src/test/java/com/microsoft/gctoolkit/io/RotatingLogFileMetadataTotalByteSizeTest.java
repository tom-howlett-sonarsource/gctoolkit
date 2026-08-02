// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void memberPathIncludesEverySegmentInRotatingSet() throws Exception {
        Path first = Files.write(directory.resolve("gc.log.0"), new byte[7]);
        Files.write(directory.resolve("gc.log.1"), new byte[11]);
        Files.write(directory.resolve("gc.log"), new byte[13]);
        Files.write(directory.resolve("other.log"), new byte[17]);

        assertEquals(31L, new RotatingLogFileMetadata(first).getTotalByteSize());
    }

    @Test
    void emptyDirectoryAndZipHaveZeroTotalSize() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyZip = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyZip).getTotalByteSize());
    }
}

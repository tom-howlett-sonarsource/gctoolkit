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
    Path tempDirectory;

    @Test
    void totalByteSizeSumsFileSegments() throws IOException {
        byte[] currentSegment = "0.100: current start\n0.200: current end\n".getBytes(StandardCharsets.UTF_8);
        byte[] rotatedSegment = "0.050: rotated start\n0.090: rotated end\n".getBytes(StandardCharsets.UTF_8);
        Files.write(tempDirectory.resolve("gc.log"), currentSegment);
        Files.write(tempDirectory.resolve("gc.log.0"), rotatedSegment);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDirectory);

        assertEquals(currentSegment.length + rotatedSegment.length, metadata.getTotalByteSize());
    }

    @Test
    void totalByteSizeSumsZipEntrySegments() throws IOException {
        byte[] currentSegment = "0.100: current start\n0.200: current end\n".getBytes(StandardCharsets.UTF_8);
        byte[] rotatedSegment = "0.050: rotated start\n0.090: rotated end\n".getBytes(StandardCharsets.UTF_8);
        Path zip = tempDirectory.resolve("gc.zip");
        try (var outputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeZipEntry(outputStream, "gc.log", currentSegment);
            writeZipEntry(outputStream, "gc.log.0", rotatedSegment);
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zip);

        assertEquals(currentSegment.length + rotatedSegment.length, metadata.getTotalByteSize());
    }

    private void writeZipEntry(ZipOutputStream outputStream, String name, byte[] content) throws IOException {
        outputStream.putNextEntry(new ZipEntry(name));
        outputStream.write(content);
        outputStream.closeEntry();
    }
}

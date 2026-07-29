// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingLogFileMetadataTest {

    private static final byte[] ROTATED = "rotated segment\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURRENT = "current segment\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void sumsEverySegmentOfADirectoryAndIgnoresSubDirectories() throws IOException {
        Files.write(directory.resolve("gc.log.0"), ROTATED);
        Files.write(directory.resolve("gc.log"), CURRENT);
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(ROTATED.length + CURRENT.length, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void sumsTheWholeSetWhenBuiltFromASingleMember() throws IOException {
        Files.write(directory.resolve("gc.log.0"), ROTATED);
        Files.write(directory.resolve("gc.log.1"), ROTATED);
        Files.write(directory.resolve("gc.log"), CURRENT);
        Files.write(directory.resolve("unrelated.log"), CURRENT);

        long expected = (2L * ROTATED.length) + CURRENT.length;
        assertEquals(expected, new RotatingLogFileMetadata(directory.resolve("gc.log.0")).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(directory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void sumsUncompressedSizeOfZipEntries() throws IOException {
        byte[] compressible = new byte[4096];
        Arrays.fill(compressible, (byte) 'a');
        Path archive = directory.resolve("gc.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(zip, "gc/", new byte[0]);
            writeEntry(zip, "gc/gc.log.0", compressible);
            writeEntry(zip, "gc/gc.log", CURRENT);
        }

        assertEquals(compressible.length + CURRENT.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
        assertTrue(Files.size(archive) < compressible.length, "the archive should be smaller than its contents");
    }

    @Test
    void returnsZeroWhenTheDirectoryHoldsNoFiles() throws IOException {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void returnsZeroWhenTheArchiveHoldsOnlyDirectoryEntries() throws IOException {
        Path archive = directory.resolve("gc.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(zip, "gc/", new byte[0]);
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void sizesRotatingLogWithoutDisturbingSegmentDiscovery() throws IOException {
        Path current = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        Path rotated = current.resolveSibling("G1-80-16gbps2.log.0");
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(current);

        assertEquals(Files.size(current) + Files.size(rotated), metadata.getTotalByteSize());
        assertEquals(2, metadata.getNumberOfFiles());
        assertEquals(2, metadata.logFiles().count());
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}

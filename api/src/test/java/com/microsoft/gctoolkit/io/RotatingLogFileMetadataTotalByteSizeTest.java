// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataTotalByteSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromNumberedMemberWithoutChangingOrder() throws Exception {
        byte[] first = ("[0.001s][info][gc] first\n"
                + "[0.002s][info][gc] first end\n").getBytes(StandardCharsets.UTF_8);
        byte[] current = ("[0.003s][info][gc] current\n"
                + "[0.004s][info][gc] current end\n").getBytes(StandardCharsets.UTF_8);
        Path numberedMember = Files.write(directory.resolve("gc.log.0"), first);
        Files.write(directory.resolve("gc.log"), current);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(numberedMember);
        List<String> order = metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());

        assertEquals(first.length + current.length, metadata.getTotalByteSize());
        assertEquals(order, metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList()));
    }

    @Test
    void returnsZeroForEmptyDirectoryAndZip() throws Exception {
        Path emptyDirectory = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(emptyDirectory).getTotalByteSize());

        Path emptyArchive = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyArchive))) {
            // Create an empty, valid ZIP archive.
        }
        assertEquals(0L, new RotatingLogFileMetadata(emptyArchive).getTotalByteSize());
    }

    @Test
    void usesUncompressedZipEntrySizesAndIgnoresDirectories() throws Exception {
        byte[] content = new byte[4096];
        Path archive = directory.resolve("compressed.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(content);
            output.closeEntry();
        }

        assertEquals(content.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

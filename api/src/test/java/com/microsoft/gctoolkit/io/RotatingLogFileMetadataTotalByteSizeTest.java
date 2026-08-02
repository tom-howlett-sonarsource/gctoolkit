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
    void emptyDirectoryHasNoBytes() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("empty"));
        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void directorySkipsNestedDirectories() throws Exception {
        byte[] content = "some log content\n".getBytes(StandardCharsets.UTF_8);
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.createDirectory(logs.resolve("nested"));
        Files.write(logs.resolve("gc.log"), content);
        assertEquals(content.length, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void memberOfRotatingSetSumsWholeSet() throws Exception {
        byte[] zero = "segment zero\n".getBytes(StandardCharsets.UTF_8);
        byte[] one = "segment one\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active segment\n".getBytes(StandardCharsets.UTF_8);
        byte[] unrelated = "not part of the set\n".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("gc.log.0"), zero);
        Files.write(directory.resolve("gc.log.1"), one);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("other.log"), unrelated);

        long expected = zero.length + one.length + active.length;
        assertEquals(expected,
                new RotatingLogFileMetadata(directory.resolve("gc.log.1")).getTotalByteSize());
        assertEquals(expected,
                new RotatingLogFileMetadata(directory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void zipWithOnlyDirectoryEntriesHasNoBytes() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void zipSumsNestedEntriesUncompressed() throws Exception {
        byte[] first = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n".getBytes(StandardCharsets.UTF_8);
        byte[] second = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n".getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("nested.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log.0"));
            output.write(first);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(second);
            output.closeEntry();
        }
        // highly compressible content: the answer must be the uncompressed total
        assertEquals(first.length + second.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void discoveryAndOrderingStillWorkForRealRotatingLogs() throws Exception {
        Path logs = Files.createDirectory(directory.resolve("unified"));
        Files.write(logs.resolve("gc.log.0"), "[0.100s][info][gc] first\n[1.000s][info][gc] first\n"
                .getBytes(StandardCharsets.UTF_8));
        Files.write(logs.resolve("gc.log"), "[2.000s][info][gc] current\n[3.000s][info][gc] current\n"
                .getBytes(StandardCharsets.UTF_8));

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(logs);
        assertEquals(2, metadata.getNumberOfFiles());
        assertEquals("gc.log.0", metadata.logFiles().findFirst().get().getSegmentName());
        assertEquals(Files.size(logs.resolve("gc.log.0")) + Files.size(logs.resolve("gc.log")),
                metadata.getTotalByteSize());
    }
}

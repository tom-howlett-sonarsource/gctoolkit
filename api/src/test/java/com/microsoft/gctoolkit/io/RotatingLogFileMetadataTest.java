// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RotatingLogFileMetadataTest {

    private static final byte[] ROTATED = "rotated log\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CURRENT = "current log\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path directory;

    @Test
    void totalByteSizeOfDirectorySumsSegmentsAndActiveLog() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log.0"), ROTATED);
        Files.write(logs.resolve("gc.log.1"), ROTATED);
        Files.write(logs.resolve("gc.log"), CURRENT);

        assertEquals((2L * ROTATED.length) + CURRENT.length,
                new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfDirectoryIgnoresNestedDirectories() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        Files.write(logs.resolve("gc.log"), CURRENT);
        Files.createDirectory(logs.resolve("nested"));

        assertEquals(CURRENT.length, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfEmptyDirectoryIsZero() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));

        assertEquals(0L, new RotatingLogFileMetadata(logs).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfMemberOfRotatingSetCoversTheWholeSet() throws IOException {
        Path first = Files.write(directory.resolve("gc.log.0"), ROTATED);
        Path current = Files.write(directory.resolve("gc.log"), CURRENT);
        Files.write(directory.resolve("other.log"), CURRENT);
        long expected = Files.size(first) + Files.size(current);

        assertEquals(expected, new RotatingLogFileMetadata(first).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(current).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfZipUsesUncompressedSizeOfFileEntries() throws IOException {
        Path archive = zip(directory.resolve("logs.zip"), "logs/", "gc.log.0", "gc.log");

        assertEquals(ROTATED.length + CURRENT.length,
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfZipHoldingOnlyDirectoriesIsZero() throws IOException {
        Path archive = zip(directory.resolve("logs.zip"), "logs/");

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfUnsupportedFormatIsZero() throws IOException {
        Path archive = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write(CURRENT);
        }

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeMatchesTheDiscoveredSegments() throws IOException {
        Path member = new TestLogFile("G1-80-16gbps2.log.0").getFile().toPath();
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(member);
        List<LogFileSegment> discovered = metadata.logFiles().collect(Collectors.toList());
        long expected = 0L;
        for (LogFileSegment segment : discovered)
            expected += Files.size(segment.getPath());

        assertEquals(2, discovered.size());
        assertEquals(expected, metadata.getTotalByteSize());
    }

    private Path zip(Path archive, String... entryNames) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                if (!entryName.endsWith("/"))
                    output.write(entryName.matches(".+\\.\\d+$") ? ROTATED : CURRENT);
                output.closeEntry();
            }
        }
        return archive;
    }
}

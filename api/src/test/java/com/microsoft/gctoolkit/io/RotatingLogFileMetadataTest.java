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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RotatingLogFileMetadataTest {

    private static final byte[] CURRENT = "current segment\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ROTATED = "rotated segment\n".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void totalByteSizeOfDirectoryIncludesEverySegment() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("logs"));
        Files.write(directory.resolve("gc.log"), CURRENT);
        Files.write(directory.resolve("gc.log.0"), ROTATED);
        Files.write(directory.resolve("gc.log.1"), ROTATED);

        assertEquals(CURRENT.length + (2L * ROTATED.length),
                new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfDirectoryIgnoresNestedDirectories() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("logs"));
        Files.write(directory.resolve("gc.log"), CURRENT);
        Files.createDirectory(directory.resolve("archive"));

        assertEquals(CURRENT.length, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfEmptyDirectoryIsZero() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("logs"));

        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfSingleMemberCoversTheWholeRotatingSet() throws IOException {
        Path rotated = temporaryDirectory.resolve("gc.log.0");
        Files.write(temporaryDirectory.resolve("gc.log"), CURRENT);
        Files.write(rotated, ROTATED);
        Files.write(temporaryDirectory.resolve("gc.log.1"), ROTATED);
        // not a member of the gc.log rotating set
        Files.write(temporaryDirectory.resolve("other.log"), CURRENT);

        long expected = CURRENT.length + (2L * ROTATED.length);
        assertEquals(expected, new RotatingLogFileMetadata(rotated).getTotalByteSize());
        assertEquals(expected, new RotatingLogFileMetadata(temporaryDirectory.resolve("gc.log")).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfRotatingLogMatchesSegmentsOnDisk() throws IOException {
        Path current = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        long expected = Files.size(current) + Files.size(current.resolveSibling("G1-80-16gbps2.log.0"));

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(current);
        assertEquals(expected, metadata.getTotalByteSize());
        // discovery and ordering are unchanged by asking for the size
        assertEquals(2, metadata.getNumberOfFiles());
        assertEquals(2, metadata.logFiles().count());
    }

    @Test
    void totalByteSizeOfZipSumsUncompressedEntries() throws IOException {
        Path archive = writeZip("logs.zip", "logs/", "gc.log", "gc.log.0", "gc.log.1");

        assertEquals(CURRENT.length + (2L * ROTATED.length),
                new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfZipHoldingOnlyDirectoriesIsZero() throws IOException {
        Path archive = writeZip("logs.zip", "logs/", "logs/archive/");

        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void totalByteSizeOfUnsupportedFormatIsZero() throws IOException {
        Path gzip = temporaryDirectory.resolve("gc.log.gz");
        Files.write(gzip, new byte[]{(byte) LogFileMetadata.GZIP_MAGIC1, (byte) LogFileMetadata.GZIP_MAGIC2, 0, 0});

        assertEquals(0L, new RotatingLogFileMetadata(gzip).getTotalByteSize());
    }

    /**
     * Write a Zip file, entry names ending in {@code /} become directory entries and the
     * remaining entries hold either the current or a rotated segment.
     */
    private Path writeZip(String name, String... entryNames) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String entryName : entryNames) {
                zip.putNextEntry(new ZipEntry(entryName));
                if (!entryName.endsWith("/"))
                    writeSegment(zip, entryName);
                zip.closeEntry();
            }
        }
        return archive;
    }

    private void writeSegment(OutputStream stream, String entryName) throws IOException {
        stream.write(entryName.matches(".+\\.\\d+$") ? ROTATED : CURRENT);
    }
}

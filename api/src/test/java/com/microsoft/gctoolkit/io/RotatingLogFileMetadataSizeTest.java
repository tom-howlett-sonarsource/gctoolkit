package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsMembersFromAnIndividualSegmentWithoutIncludingOtherLogs() throws Exception {
        Path member = Files.writeString(directory.resolve("gc.log.0"), "older log\n");
        Path active = Files.writeString(directory.resolve("gc.log"), "active log\n");
        Files.writeString(directory.resolve("other.log"), "unrelated log\n");
        Files.createDirectory(directory.resolve("gc.log.directory"));

        assertEquals(Files.size(member) + Files.size(active),
                new RotatingLogFileMetadata(member).getTotalByteSize());
    }

    @Test
    void includesDiscoveredSegmentsEvenWhenTheirTimestampsOverlap() throws Exception {
        Path member = Files.writeString(directory.resolve("gc.log.0"), "[1.000s] first\n[3.000s] last\n");
        Path active = Files.writeString(directory.resolve("gc.log"), "[2.000s] first\n[4.000s] last\n");
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);
        long expected = Files.size(member) + Files.size(active);

        assertEquals(expected, metadata.getTotalByteSize());
        metadata.logFiles().close();
        assertEquals(expected, metadata.getTotalByteSize());
    }

    @Test
    void emptyDirectoryAndDirectoryOnlyZipHaveZeroSize() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void sumsAllZipEntriesWithoutRequiringRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("first.txt"));
            output.write(new byte[1024]);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("nested/second.txt"));
            output.write(new byte[2048]);
            output.closeEntry();
        }
        assertEquals(3072L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

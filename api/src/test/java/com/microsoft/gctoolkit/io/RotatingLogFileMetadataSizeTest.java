package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsAllDiscoveredFilesFromAnyMemberWithoutChangingOrdering() throws Exception {
        Path current = Files.writeString(directory.resolve("gc.log"), "[2.000s] current\n");
        Path first = Files.writeString(directory.resolve("gc.log.0"), "[0.000s] first\n[1.000s] end\n");
        Path overlapping = Files.writeString(directory.resolve("gc.log.1"), "[1.000s] overlapping\n[3.000s] end\n");
        Files.writeString(directory.resolve("other.log"), "unrelated\n");
        long expected = Files.size(current) + Files.size(first) + Files.size(overlapping);

        for (Path member : List.of(current, first, overlapping)) {
            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(member);
            assertEquals(expected, metadata.getTotalByteSize());
            List<String> ordered = metadata.logFiles().map(LogFileSegment::getSegmentName).collect(Collectors.toList());
            assertEquals(expected, metadata.getTotalByteSize());
            assertEquals(ordered, metadata.logFiles().map(LogFileSegment::getSegmentName).collect(Collectors.toList()));
        }
    }

    @Test
    void emptyDirectoryAndDirectoriesOnlyHaveZeroSize() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Files.createDirectory(directory.resolve("nested"));
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void zipSizesIncludeEveryFileRegardlessOfRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        byte[] content = new byte[10000];
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            for (String name : List.of("nested/first.log", "unrelated.txt")) {
                output.putNextEntry(new ZipEntry(name));
                output.write(content);
                output.closeEntry();
            }
        }
        assertEquals(20000L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void emptyAndDirectoryOnlyZipsHaveZeroSize() throws Exception {
        Path archive = directory.resolve("empty.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
        }
        assertEquals(0L, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }
}

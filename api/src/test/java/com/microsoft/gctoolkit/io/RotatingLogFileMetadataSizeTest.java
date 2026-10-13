package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsAllDiscoveredFilesFromAnyMember() throws Exception {
        Path archived = directory.resolve("gc.log.0");
        Path active = directory.resolve("gc.log");
        Files.writeString(archived, "[1.000s] archived\n", StandardCharsets.UTF_8);
        Files.writeString(active, "[1.000s] active\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("other.log"), "unrelated");
        long expected = Files.size(archived) + Files.size(active);

        for (Path member : new Path[]{archived, active}) {
            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(member);
            assertEquals(expected, metadata.getTotalByteSize());
            metadata.logFiles().count();
            assertEquals(expected, metadata.getTotalByteSize());
        }
    }

    @Test
    void supportsPreUnifiedCurrentMember() throws Exception {
        Path archived = directory.resolve("gc.log.0");
        Path active = directory.resolve("gc.log.1.current");
        Files.writeString(archived, "archived\n");
        Files.writeString(active, "active\n");

        assertEquals(Files.size(archived) + Files.size(active),
                new RotatingLogFileMetadata(active).getTotalByteSize());
    }

    @Test
    void excludesDirectoriesAndReturnsZeroForEmptyDirectory() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Path nested = Files.createDirectory(directory.resolve("nested"));
        Files.writeString(nested.resolve("gc.log"), "not a direct segment");
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());
        Path active = Files.writeString(directory.resolve("gc.log"), "active\n");
        assertEquals(Files.size(active), new RotatingLogFileMetadata(directory).getTotalByteSize());
    }

    @Test
    void sumsAllZipEntriesWithoutRequiringRotatingNames() throws Exception {
        Path archive = directory.resolve("logs.zip");
        byte[] content = "repeated log content\n".repeat(1000).getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("nested/first.txt"));
            output.write(content);
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.txt"));
            output.write(content);
            output.closeEntry();
        }
        assertEquals(2L * content.length, new RotatingLogFileMetadata(archive).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyAndDirectoryOnlyZipFiles() throws Exception {
        Path archive = directory.resolve("logs.zip");
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

package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path directory;

    @Test
    void sumsSegmentsWhenConstructedFromAnIndividualMember() throws Exception {
        byte[] archived = "archived log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path archivedLog = Files.write(directory.resolve("gc.log.0"), archived);
        Files.write(directory.resolve("gc.log"), active);
        Files.write(directory.resolve("unrelated.log"), new byte[31]);

        assertEquals(archived.length + active.length,
                new RotatingLogFileMetadata(archivedLog).getTotalByteSize());
    }

    @Test
    void returnsZeroForInputsWithoutEligibleEntries() throws Exception {
        assertEquals(0L, new RotatingLogFileMetadata(directory).getTotalByteSize());

        Path emptyArchive = directory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyArchive))) {
            // Create an empty ZIP archive.
        }

        assertEquals(0L, new RotatingLogFileMetadata(emptyArchive).getTotalByteSize());
    }
}

package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingLogFileMetadataSizeTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void sumsRotatingSetWhenConstructedFromMember() throws IOException {
        byte[] archived = "archived log\n".getBytes(StandardCharsets.UTF_8);
        byte[] active = "active log\n".getBytes(StandardCharsets.UTF_8);
        Path archivedPath = Files.write(temporaryDirectory.resolve("gc.log.0"), archived);
        Files.write(temporaryDirectory.resolve("gc.log.current"), active);

        assertEquals(archived.length + active.length,
                new RotatingLogFileMetadata(archivedPath).getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws IOException {
        assertEquals(0L, new RotatingLogFileMetadata(temporaryDirectory).getTotalByteSize());
    }
}

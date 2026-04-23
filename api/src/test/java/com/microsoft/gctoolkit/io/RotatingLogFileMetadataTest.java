package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingLogFileMetadataTest {

    // Pre-unified GC log format: "date: uptime: [event]"
    // Each segment needs multiple lines so startTime != endTime (required by ordering logic)

    private static final String SEGMENT_0_CONTENT =
            "2024-01-01T00:00:00.000+0000: 0.001: [GC pause (young)]\n" +
            "2024-01-01T00:00:10.000+0000: 10.000: [GC pause (young)]\n";

    private static final String CURRENT_CONTENT =
            "2024-01-01T00:00:50.000+0000: 50.001: [GC pause (young)]\n" +
            "2024-01-01T00:01:40.000+0000: 100.000: [GC pause (young)]\n";

    @Test
    void getTotalByteSizeForPlainTextSegments(@TempDir Path tempDir) throws IOException {
        byte[] seg0 = SEGMENT_0_CONTENT.getBytes();
        byte[] current = CURRENT_CONTENT.getBytes();

        Files.write(tempDir.resolve("gc.log.0"), seg0);
        Files.write(tempDir.resolve("gc.log"), current);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);
        assertEquals(seg0.length + current.length, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeForZipSegments(@TempDir Path tempDir) throws IOException {
        Path zipPath = tempDir.resolve("gc.log.zip");
        byte[] seg0 = SEGMENT_0_CONTENT.getBytes();
        byte[] current = CURRENT_CONTENT.getBytes();

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("gc.log.0"));
            zos.write(seg0);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("gc.log"));
            zos.write(current);
            zos.closeEntry();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath);
        assertEquals(seg0.length + current.length, metadata.getTotalByteSize());
    }

    @Test
    void getTotalByteSizeSingleSegment(@TempDir Path tempDir) throws IOException {
        byte[] content = CURRENT_CONTENT.getBytes();
        Files.write(tempDir.resolve("gc.log"), content);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);
        assertEquals(content.length, metadata.getTotalByteSize());
    }

    @Test
    void gcLogFileSegmentGetByteSize(@TempDir Path tempDir) throws IOException {
        byte[] content = SEGMENT_0_CONTENT.getBytes();
        Path logFile = tempDir.resolve("gc.log.0");
        Files.write(logFile, content);

        GCLogFileSegment segment = new GCLogFileSegment(logFile);
        assertEquals(content.length, segment.getByteSize());
    }

    @Test
    void gcLogFileZipSegmentGetByteSize(@TempDir Path tempDir) throws IOException {
        Path zipPath = tempDir.resolve("gc.log.zip");
        byte[] content = SEGMENT_0_CONTENT.getBytes();

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("gc.log.0"));
            zos.write(content);
            zos.closeEntry();
        }

        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipPath, "gc.log.0");
        assertEquals(content.length, segment.getByteSize());
    }

    @Test
    void getTotalByteSizeIsPositive(@TempDir Path tempDir) throws IOException {
        Files.write(tempDir.resolve("gc.log"), CURRENT_CONTENT.getBytes());

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(tempDir);
        assertTrue(metadata.getTotalByteSize() > 0);
    }
}

package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.APPEND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RotatingLogFileMetadataTest {

    private static final byte[] FIRST_SEGMENT = ("[1.000s][info][gc] first segment\n"
            + "[2.000s][info][gc] first segment end\n").getBytes(UTF_8);
    private static final byte[] CURRENT_SEGMENT = ("[3.000s][info][gc] current segment\n"
            + "[4.000s][info][gc] current segment end\n").getBytes(UTF_8);
    private static final byte[] APPENDED_CONTENT = "[5.000s][info][gc] appended content\n".getBytes(UTF_8);

    @TempDir
    Path temporaryDirectory;

    @Test
    void totalsBytesAcrossPlainTextSegmentsAndReflectsFileGrowth() throws IOException {
        Files.write(temporaryDirectory.resolve("gc.log.0"), FIRST_SEGMENT);
        Path currentSegment = Files.write(
                temporaryDirectory.resolve("gc.log.1.current"),
                CURRENT_SEGMENT);
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(temporaryDirectory);

        assertEquals(FIRST_SEGMENT.length + CURRENT_SEGMENT.length, metadata.getTotalByteSize());

        Files.write(currentSegment, APPENDED_CONTENT, APPEND);

        assertEquals(
                FIRST_SEGMENT.length + CURRENT_SEGMENT.length + APPENDED_CONTENT.length,
                metadata.getTotalByteSize());
    }

    @Test
    void totalsUncompressedBytesAcrossZipSegments() throws IOException {
        Path zipPath = temporaryDirectory.resolve("gc-logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            writeEntry(output, "gc.log.0", FIRST_SEGMENT);
            writeEntry(output, "gc.log.1.current", CURRENT_SEGMENT);
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath);

        assertEquals(FIRST_SEGMENT.length + CURRENT_SEGMENT.length, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroWhenThereAreNoSegments() throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(temporaryDirectory);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void failsWhenZipEntryCannotBeFound() throws IOException {
        Path zipPath = temporaryDirectory.resolve("gc-logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            writeEntry(output, "gc.log.0", FIRST_SEGMENT);
        }
        GCLogFileZipSegment missingSegment = new GCLogFileZipSegment(zipPath, "missing.log");

        assertThrows(IOException.class, missingSegment::getByteSize);
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

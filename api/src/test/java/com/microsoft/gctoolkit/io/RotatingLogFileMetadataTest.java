package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RotatingLogFileMetadataTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void totalsFileSystemSegmentSizes() throws IOException {
        byte[] firstLog = "[0.001s] first\n[0.002s] first end\n".getBytes(StandardCharsets.UTF_8);
        byte[] currentLog = "[1.001s] current\n[1.002s] current end\n".getBytes(StandardCharsets.UTF_8);
        Files.write(temporaryDirectory.resolve("gc.log.0"), firstLog);
        Files.write(temporaryDirectory.resolve("gc.log"), currentLog);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(temporaryDirectory);

        assertEquals(firstLog.length + currentLog.length, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroForEmptyDirectory() throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(temporaryDirectory);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void totalsUncompressedZipEntrySizes() throws IOException {
        byte[] firstLog = "[0.001s] repeated repeated repeated\n[0.002s] first end\n".getBytes(StandardCharsets.UTF_8);
        byte[] currentLog = "[1.001s] repeated repeated repeated\n[1.002s] current end\n".getBytes(StandardCharsets.UTF_8);
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeEntry(output, "gc.log.0", firstLog);
            writeEntry(output, "gc.log", currentLog);
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);

        assertEquals(firstLog.length + currentLog.length, metadata.getTotalByteSize());
    }

    @Test
    void throwsWhenDiscoveredSegmentCannotBeSized() throws IOException {
        Path log = Files.writeString(temporaryDirectory.resolve("gc.log"), "[0.001s] current\n");
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(temporaryDirectory);
        metadata.logFiles().count();
        Files.delete(log);

        assertThrows(IOException.class, metadata::getTotalByteSize);
    }

    private static void writeEntry(ZipOutputStream output, String name, byte[] contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents);
        output.closeEntry();
    }
}

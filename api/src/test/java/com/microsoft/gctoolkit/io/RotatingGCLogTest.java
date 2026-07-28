package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class RotatingGCLogTest {

    @Test
    void orderRotatingLogsTest() {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        try {
            RotatingGCLogFile file = new RotatingGCLogFile(path);
            assertEquals(2, file.getMetaData().getNumberOfFiles());
            assertEquals(2, file.getMetaData().logFiles().map(LogFileSegment::getPath).map(Path::toFile).map(File::getName).filter(s -> s.startsWith("G1-80-16gbps2")).count());
            file.getMetaData().logFiles().map(LogFileSegment::getEndTime).forEach(System.out::println);
        } catch (IOException ioe) {
            fail(ioe);
        }
    }

    @Test
    void calculatesTotalByteSizeForFileSegments() throws IOException {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(path);
        long expectedSize = metadata.logFiles()
                .map(LogFileSegment::getPath)
                .mapToLong(segmentPath -> segmentPath.toFile().length())
                .sum();

        assertEquals(expectedSize, metadata.getTotalByteSize());
    }

    @Test
    void calculatesTotalByteSizeForZipSegments() throws IOException {
        Path path = new TestLogFile("rolling/jdk14/rollinglogs/zip/rollover.zip").getFile().toPath();
        long expectedSize;
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            expectedSize = zipFile.stream()
                    .filter(zipEntry -> !zipEntry.isDirectory())
                    .mapToLong(ZipEntry::getSize)
                    .sum();
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(path);
        assertEquals(expectedSize, metadata.getTotalByteSize());
    }

    @Test
    void returnsZeroByteSizeWhenNoSegmentsArePresent(@TempDir Path directory) throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);

        assertEquals(0L, metadata.getTotalByteSize());
    }

    @Test
    void failsWhenZipSegmentCannotBeFound() {
        Path path = new TestLogFile("rolling/jdk14/rollinglogs/zip/rollover.zip").getFile().toPath();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(path, "missing.log");

        IOException exception = assertThrows(IOException.class, segment::getByteSize);
        assertEquals("Unable to determine the size of ZIP entry: missing.log", exception.getMessage());
    }
}

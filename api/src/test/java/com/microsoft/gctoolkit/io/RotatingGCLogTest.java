package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingGCLogTest {

    @Test
    void orderRotatingLogsTest() {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        assertDoesNotThrow(() -> {
            RotatingGCLogFile file = new RotatingGCLogFile(path);
            assertEquals(2, file.getMetaData().getNumberOfFiles());
            assertEquals(2, file.getMetaData().logFiles().map(LogFileSegment::getPath).map(Path::toFile).map(File::getName).filter(s -> s.startsWith("G1-80-16gbps2")).count());
            file.getMetaData().logFiles().map(LogFileSegment::getEndTime).forEach(System.out::println);
        });
    }

    @Test
    void totalByteSizeForRotatingFileMember() throws IOException {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(path);
        long expected = sumFileSystemSegments(metadata);
        List<Path> segmentPathsBefore = collectSegmentPaths(metadata);

        long actual = metadata.getTotalByteSize();

        assertEquals(expected, actual);
        assertTrue(actual > 0L);
        assertEquals(segmentPathsBefore, collectSegmentPaths(metadata),
                "getTotalByteSize must not alter existing discovery or ordering");
    }

    @Test
    void totalByteSizeForDirectoryInput() throws IOException {
        Path directory = new TestLogFile("rotating_directory").getFile().toPath();
        assertTrue(Files.isDirectory(directory));
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(directory);
        long expected = sumFileSystemSegments(metadata);
        List<Path> segmentPathsBefore = collectSegmentPaths(metadata);

        long actual = metadata.getTotalByteSize();

        assertEquals(expected, actual);
        assertTrue(actual > 0L);
        assertEquals(segmentPathsBefore, collectSegmentPaths(metadata),
                "getTotalByteSize must not alter existing discovery or ordering");
    }

    @Test
    void totalByteSizeForZipInput() throws IOException {
        Path zipPath = new TestLogFile("rotating.zip").getFile().toPath();
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath);
        long expected = sumUncompressedZipEntries(zipPath);
        List<String> segmentNamesBefore = metadata.logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());

        long actual = metadata.getTotalByteSize();

        assertEquals(expected, actual);
        assertTrue(actual > 0L);
        assertEquals(segmentNamesBefore,
                metadata.logFiles().map(LogFileSegment::getSegmentName).collect(Collectors.toList()),
                "getTotalByteSize must not alter existing discovery or ordering");
    }

    @Test
    void totalByteSizeReturnsZeroForEmptyDirectory(@org.junit.jupiter.api.io.TempDir Path emptyDir) throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(emptyDir);
        assertEquals(0L, metadata.getTotalByteSize());
    }

    private static long sumFileSystemSegments(RotatingLogFileMetadata metadata) {
        return metadata.logFiles()
                .map(LogFileSegment::getPath)
                .filter(Files::isRegularFile)
                .mapToLong(RotatingGCLogTest::sizeOf)
                .sum();
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Path> collectSegmentPaths(RotatingLogFileMetadata metadata) {
        return metadata.logFiles().map(LogFileSegment::getPath).collect(Collectors.toList());
    }

    private static long sumUncompressedZipEntries(Path zipPath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            return zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .mapToLong(ZipEntry::getSize)
                    .filter(size -> size >= 0L)
                    .sum();
        }
    }
}

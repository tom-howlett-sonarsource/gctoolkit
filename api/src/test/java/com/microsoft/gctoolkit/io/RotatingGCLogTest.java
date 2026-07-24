package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void totalByteSizeFromRotatingMemberTest() {
        Path path = new TestLogFile("G1-80-16gbps2.log").getFile().toPath();
        try {
            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(path);
            long expected = Files.size(path.resolveSibling("G1-80-16gbps2.log"))
                    + Files.size(path.resolveSibling("G1-80-16gbps2.log.0"));
            assertEquals(expected, metadata.getTotalByteSize());
        } catch (IOException ioe) {
            fail(ioe);
        }
    }

    @Test
    void totalByteSizeFromDirectoryTest() {
        Path path = new TestLogFile("rotating_directory").getFile().toPath();
        try {
            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(path);
            long expected = 0L;
            try (var listing = Files.list(path)) {
                expected = listing.filter(Files::isRegularFile).mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).sum();
            }
            assertEquals(expected, metadata.getTotalByteSize());
        } catch (IOException ioe) {
            fail(ioe);
        }
    }

    @Test
    void totalByteSizeFromZipTest() {
        Path path = new TestLogFile("rotating.zip").getFile().toPath();
        try {
            RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(path);
            // uncompressed sizes of gc.log.0 (3146662) + gc.log.1.current (1220305)
            assertEquals(3146662L + 1220305L, metadata.getTotalByteSize());
        } catch (IOException ioe) {
            fail(ioe);
        }
    }
}

package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RotatingGCLogTest {

    @TempDir
    Path temporaryDirectory;

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
        byte[] firstSegment = "[0.001s] first event\n[0.002s] second event\n".getBytes(StandardCharsets.UTF_8);
        byte[] currentSegment = "[1.001s] first event\n[1.002s] second event\n".getBytes(StandardCharsets.UTF_8);
        Files.write(temporaryDirectory.resolve("gc.log.0"), firstSegment);
        Files.write(temporaryDirectory.resolve("gc.log"), currentSegment);

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(temporaryDirectory);

        assertEquals(firstSegment.length + currentSegment.length, metadata.getTotalByteSize());
    }

    @Test
    void calculatesTotalByteSizeForZipSegments() throws IOException {
        byte[] firstSegment = "[0.001s] first event\n[0.002s] second event\n".getBytes(StandardCharsets.UTF_8);
        byte[] currentSegment = "[1.001s] first event\n[1.002s] second event\n".getBytes(StandardCharsets.UTF_8);
        Path archive = temporaryDirectory.resolve("gc-logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            writeZipEntry(output, "gc.log.0", firstSegment);
            writeZipEntry(output, "gc.log", currentSegment);
        }

        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(archive);

        assertEquals(firstSegment.length + currentSegment.length, metadata.getTotalByteSize());
    }

    private static void writeZipEntry(ZipOutputStream output, String name, byte[] content) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(content);
        output.closeEntry();
    }
}

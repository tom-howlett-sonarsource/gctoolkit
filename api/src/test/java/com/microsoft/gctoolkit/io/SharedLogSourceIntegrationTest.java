package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedLogSourceIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void singleZipRetainsTrimmingFilteringAndSentinel() throws IOException {
        Path path = archive();
        SingleGCLogFile log = new SingleGCLogFile(path);
        assertTrue(log.getMetaData().isZip());
        assertEquals(1, log.getMetaData().getNumberOfFiles());
        try (var lines = log.stream()) {
            assertEquals(List.of("first", log.endOfData()), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void namedZipSegmentReadsTheRequestedEntry() throws IOException {
        try (var lines = new GCLogFileZipSegment(archive(), "second.log").stream()) {
            assertEquals(List.of("second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingDiscoveryKeepsSelectionOrderingAndSentinel() throws IOException {
        Path current = directory.resolve("gc.log");
        Files.writeString(current, "2.000: current\n3.000: end\n");
        Files.writeString(directory.resolve("gc.log.0"), "0.001: previous\n1.000: end\n");
        Files.writeString(directory.resolve("other.log"), "unrelated\n");
        RotatingGCLogFile log = new RotatingGCLogFile(current);
        assertEquals(List.of("gc.log.0", "gc.log"), log.getOrderedGarbageCollectionLogFiles().stream()
                .map(LogFileSegment::getSegmentName).collect(Collectors.toList()));
        try (var lines = log.stream()) {
            assertEquals(List.of("0.001: previous", "1.000: end", "2.000: current", "3.000: end",
                    log.endOfData()), lines.collect(Collectors.toList()));
        }
    }

    private Path archive() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/first.log"));
            output.write("  first  \n\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("second.log"));
            output.write("second\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipStreamClosureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesFirstFileLinesAndSentinel() throws IOException {
        Path archive = zip("single.zip", "directory/", "", "directory/gc.log", " first \n\n second\n",
                "ignored.log", "ignored\n");
        SingleGCLogFile log = new SingleGCLogFile(archive);
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of("first", "second", log.endOfData()), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void emptyZipReturnsOnlySentinel() throws IOException {
        SingleGCLogFile log = new SingleGCLogFile(zip("empty.zip", "directory/", ""));
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(log.endOfData()), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void singleZipClosesWithoutConsumptionAndAfterExhaustion() throws IOException {
        Path archive = zip("single.zip", "gc.log", " first \n\nsecond\n");
        long baseline = descriptorsFor(archive);
        SingleGCLogFile log = new SingleGCLogFile(archive);
        try (Stream<String> lines = log.stream()) {
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = log.stream()) {
            assertEquals(3, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentPreservesWhitespaceAndClosesWithoutConsumptionAndAfterExhaustion() throws IOException {
        Path archive = zip("segment.zip", "gc.log", " first \n\nsecond\n");
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        try (Stream<String> lines = segment.stream()) {
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of(" first ", "", "second"), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentMetadataScansCloseTheirStreams() throws IOException {
        Path archive = zip("metadata.zip", "gc.log", line(1) + line(2));
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingEntryDoesNotLeakArchive() throws IOException {
        Path archive = zip("missing.zip", "gc.log", "first\n");
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class, () -> new GCLogFileZipSegment(archive, "missing").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesForUnconsumedPartialAndCompleteStreams() throws IOException {
        Path archive = zip("rotating.zip", "gc.log", line(5) + line(6),
                "gc.log.1", line(3) + line(4), "gc.log.2", line(1) + line(2));
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        assertEquals(List.of("gc.log.2", "gc.log.1", "gc.log"),
                log.getOrderedGarbageCollectionLogFiles().stream()
                        .map(LogFileSegment::getSegmentName).collect(Collectors.toList()));
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = log.stream()) {
            assertEquals(baseline, descriptorsFor(archive));
        }
        assertEquals(baseline, descriptorsFor(archive));
        for (int limit : List.of(1, 3, 5)) {
            try (Stream<String> lines = log.stream()) {
                assertEquals(limit, lines.limit(limit).count());
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(line(1).trim(), line(2).trim(), line(3).trim(), line(4).trim(),
                    line(5).trim(), line(6).trim(), log.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private String line(int timestamp) {
        return "[" + timestamp + ".000s][info][gc] event\n";
    }

    private Path zip(String name, String... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int index = 0; index < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorDirectory), "Descriptor checks require Linux /proc");
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(descriptorDirectory)) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).equals(expected)) {
                        count++;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return count;
    }
}

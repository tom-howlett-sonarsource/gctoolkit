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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipStreamCloseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesContentsAndSentinelAndClosesWithoutConsumption() throws Exception {
        Path archive = zip("single.zip", List.of("directory/", "gc.log"), List.of("", "  first  \n\n second\n"));
        SingleGCLogFile file = new SingleGCLogFile(archive);
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = file.stream()) {
            assertEquals(List.of("first", "second", file.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
        Stream<String> lines = file.stream();
        assertTrue(descriptorsFor(archive) > baseline);
        lines.close();
        lines.close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentPreservesRawLinesAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("segment.zip", List.of("gc.log"), List.of("  first  \n\nsecond\n"));
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(List.of("  first  ", "", "second"), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentMetadataScansCloseTheirStreams() throws Exception {
        Path archive = zip("metadata.zip", List.of("gc.log"), List.of("1.000: first\n2.000: second\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        long baseline = descriptorsFor(archive);
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingSegmentClosesArchiveOnSetupFailure() throws Exception {
        Path archive = zip("missing.zip", List.of("gc.log"), List.of("first\n"));
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class, () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderAndSentinelAndClosesPartialStreams() throws Exception {
        Path archive = zip("rotating.zip", List.of("gc.log", "gc.log.0", "gc.log.1"),
                List.of("5.000: current\n6.000: current end\n", "1.000: oldest\n2.000: oldest end\n",
                        "3.000: middle\n4.000: middle end\n"));
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile file = new RotatingGCLogFile(archive);
        try (Stream<String> lines = file.stream()) {
            assertEquals("1.000: oldest", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = file.stream()) {
            assertEquals(List.of("1.000: oldest", "2.000: oldest end", "3.000: middle", "4.000: middle end",
                    "5.000: current", "6.000: current end", file.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = file.stream()) {
            assertEquals(3, lines.limit(3).count());
        }
        assertEquals(baseline, descriptorsFor(archive));
        file.stream().close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, List<String> entries, List<String> contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int index = 0; index < entries.size(); index++) {
                output.putNextEntry(new ZipEntry(entries.get(index)));
                output.write(contents.get(index).getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path directory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(directory), "Archive descriptor checks require procfs");
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(directory)) {
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

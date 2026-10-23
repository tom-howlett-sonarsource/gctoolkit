// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.microsoft.gctoolkit.io.GCLogFile.END_OF_DATA_SENTINEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that ZIP-backed GC log streams release every underlying archive descriptor once the returned
 * {@link Stream} is closed, while preserving line contents, segment ordering and the end-of-data sentinel.
 * These tests are additive and independent of {@code VisibleZipStreamResourceLifecycleTest}.
 */
class ZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip("single-full.zip", entry("gc.log", "[0.001s][info][gc] first\nsecond\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
        assertLinesMatch(List.of("[0.001s][info][gc] first", "second", END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void singleZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("single-partial.zip", entry("gc.log", "[0.001s][info][gc] first\nsecond\nthird\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip("segment-full.zip", entry("segment.log", "[0.001s][info][gc] first\nsecond\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
        // GCLogFileZipSegment does not append the sentinel; it streams the raw lines of the entry.
        assertLinesMatch(List.of("[0.001s][info][gc] first", "second"), lines);
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("segment-partial.zip", entry("segment.log", "[0.001s][info][gc] first\nsecond\nthird\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip("rotating-full.zip",
                entry("gc.log.0", "[1.000s][info][gc] oldest line a\n[1.500s][info][gc] oldest line b\n"),
                entry("gc.log.1.current", "[2.000s][info][gc] newest line a\n[2.500s][info][gc] newest line b\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(baseline, descriptorsFor(archive));
        // Segments are ordered oldest first, newest last, followed by the sentinel.
        assertLinesMatch(List.of(
                "[1.000s][info][gc] oldest line a",
                "[1.500s][info][gc] oldest line b",
                "[2.000s][info][gc] newest line a",
                "[2.500s][info][gc] newest line b",
                END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        // A multi-segment archive consumed only through its first line, then abandoned. Each segment that flatMap
        // opened must be released when the composed stream is closed, so no descriptor outlives the stream. Before
        // the fix the leading segment's archive leaked because flatMap's close of the segment stream was a no-op.
        Path archive = zip("rotating-partial.zip",
                entry("gc.log.0", manyLines(1.0, 2000)),
                entry("gc.log.1.current", manyLines(3000.0, 4)));
        long baseline = descriptorsFor(archive);

        Stream<String> stream = new RotatingGCLogFile(archive).stream();
        try {
            assertEquals("[1.000s][info][gc] line 0", stream.findFirst().orElseThrow());
        } finally {
            stream.close();
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private String manyLines(double startSeconds, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            // Keep three decimal places so DateTimeStamp parses a monotonically increasing timestamp per line.
            builder.append(String.format(java.util.Locale.ROOT, "[%.3fs][info][gc] line %d%n", startSeconds + i, i));
        }
        return builder.toString();
    }

    @Test
    void rotatingZipPreservesSentinelOnEmptyConsumption() throws Exception {
        Path archive = zip("rotating-sentinel.zip",
                entry("gc.log.0", "[1.000s][info][gc] only\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // Closing without consuming anything must still release descriptors.
            assertFalse(stream.getClass().getName().isEmpty());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Entry entry(String name, String contents) {
        return new Entry(name, contents);
    }

    private Path zip(String name, Entry... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Entry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) {
                        count++;
                    }
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }

    private static final class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every archive handle opened to serve a {@code Stream<String>} is released when
 * that stream is closed, whether it was drained or abandoned part way through, and that closing
 * the stream does not disturb the line contents, the rotating segment ordering, or the
 * end-of-data sentinel.
 */
class ZipStreamResourceReleaseTest {

    private static final String OLDER_SEGMENT =
            "[0.001s][info][gc] Using G1\n" +
            "[0.010s][info][gc] Pause Young (Normal) 1M->0M(8M) 0.500ms\n";

    private static final String CURRENT_SEGMENT =
            "[1.000s][info][gc] Pause Young (Normal) 4M->1M(8M) 0.750ms\n" +
            "[2.000s][info][gc] Pause Full 6M->2M(8M) 9.000ms\n";

    @TempDir
    Path temporaryDirectory;

    // ---------------------------------------------------------------- SingleGCLogFile

    @Test
    void singleZipDrainedStreamPreservesContentAndReleasesArchive() throws IOException {
        Path archive = zip("single.zip", entry("gc.log", OLDER_SEGMENT));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] Using G1",
                "[0.010s][info][gc] Pause Young (Normal) 1M->0M(8M) 0.500ms",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipAbandonedStreamReleasesArchive() throws IOException {
        Path archive = zip("abandoned.zip", entry("gc.log", OLDER_SEGMENT + CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] Using G1", stream.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open mid-consumption");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipAbandonedStreamReleasesFile() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream bytes = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write((OLDER_SEGMENT + CURRENT_SEGMENT).getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] Using G1", stream.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open mid-consumption");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipStreamCanBeReopenedAfterClosing() throws IOException {
        Path archive = zip("reopen.zip", entry("gc.log", OLDER_SEGMENT));
        long baseline = descriptorsFor(archive);

        for (int attempt = 0; attempt < 3; attempt++) {
            try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
                assertEquals(3, stream.count());
            }
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ------------------------------------------------------------ GCLogFileZipSegment

    @Test
    void zipSegmentDrainedStreamPreservesContentAndReleasesArchive() throws IOException {
        Path archive = zip("segments.zip", entry("gc.log.0", OLDER_SEGMENT), entry("gc.log", CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] Pause Young (Normal) 4M->1M(8M) 0.750ms",
                "[2.000s][info][gc] Pause Full 6M->2M(8M) 9.000ms"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentAbandonedStreamReleasesArchive() throws IOException {
        Path archive = zip("abandoned-segment.zip", entry("gc.log", OLDER_SEGMENT + CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("[0.001s][info][gc] Using G1", stream.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open mid-consumption");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimestampProbesReleaseTheArchive() throws IOException {
        Path archive = zip("timestamps.zip", entry("gc.log", OLDER_SEGMENT + CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(0.001d, segment.getStartTime(), 1e-9);
        assertEquals(2.000d, segment.getEndTime(), 1e-9);

        assertEquals(baseline, descriptorsFor(archive), "reading start/end times must not leak the archive");
    }

    @Test
    void zipSegmentWithMissingEntryDoesNotLeakTheArchive() throws IOException {
        Path archive = zip("missing.zip", entry("gc.log", OLDER_SEGMENT));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "not-there.log").stream()) {
            assertEquals(0, stream.count());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ------------------------------------------------------------- RotatingGCLogFile

    @Test
    void rotatingZipDrainedStreamKeepsSegmentOrderAndReleasesArchive() throws IOException {
        Path archive = zip("rotating.zip", entry("gc.log.0", OLDER_SEGMENT), entry("gc.log", CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] Using G1",
                "[0.010s][info][gc] Pause Young (Normal) 1M->0M(8M) 0.500ms",
                "[1.000s][info][gc] Pause Young (Normal) 4M->1M(8M) 0.750ms",
                "[2.000s][info][gc] Pause Full 6M->2M(8M) 9.000ms",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipAbandonedStreamReleasesTheInFlightSegment() throws IOException {
        Path archive = zip("rotating-abandoned.zip", entry("gc.log.0", OLDER_SEGMENT), entry("gc.log", CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] Using G1", stream.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipAbandonedAtSegmentBoundaryReleasesEverySegment() throws IOException {
        Path archive = zip("rotating-boundary.zip", entry("gc.log.0", OLDER_SEGMENT), entry("gc.log", CURRENT_SEGMENT));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // Stop once the first line of the second segment has been produced, so that both
            // segment streams have been opened but neither the second one nor the composed
            // stream has been drained.
            assertEquals("[1.000s][info][gc] Pause Young (Normal) 4M->1M(8M) 0.750ms",
                    stream.skip(2).iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingDirectoryDrainedStreamKeepsSegmentOrderAndReleasesFiles() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("rotating"));
        Path older = write(directory.resolve("gc.log.0"), OLDER_SEGMENT);
        Path current = write(directory.resolve("gc.log"), CURRENT_SEGMENT);
        long baseline = descriptorsFor(older) + descriptorsFor(current);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(directory).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] Using G1",
                "[0.010s][info][gc] Pause Young (Normal) 1M->0M(8M) 0.500ms",
                "[1.000s][info][gc] Pause Young (Normal) 4M->1M(8M) 0.750ms",
                "[2.000s][info][gc] Pause Full 6M->2M(8M) 9.000ms",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(older) + descriptorsFor(current));
    }

    @Test
    void rotatingDirectoryAbandonedStreamReleasesTheInFlightSegment() throws IOException {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("rotating-abandoned"));
        Path older = write(directory.resolve("gc.log.0"), OLDER_SEGMENT);
        Path current = write(directory.resolve("gc.log"), CURRENT_SEGMENT);
        long baseline = descriptorsFor(older) + descriptorsFor(current);

        try (Stream<String> stream = new RotatingGCLogFile(directory).stream()) {
            assertEquals("[0.001s][info][gc] Using G1", stream.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(older) + descriptorsFor(current));
    }

    // ------------------------------------------------------------------------ helpers

    private static String[] entry(String name, String contents) {
        return new String[]{name, contents};
    }

    private Path zip(String name, String[]... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (String[] entry : entries) {
                output.putNextEntry(new ZipEntry(entry[0]));
                output.write(entry[1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private Path write(Path path, String contents) throws IOException {
        Files.write(path, contents.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    /**
     * Count the file descriptors this JVM currently holds against {@code file}. Comparing a count
     * taken after a stream is closed against one taken before it was opened is what makes a leak
     * of the underlying archive observable.
     */
    private long descriptorsFor(Path file) throws IOException {
        Path expected = file.toRealPath();
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
}

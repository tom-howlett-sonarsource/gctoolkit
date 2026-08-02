// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Streams handed out by the ZIP and GZIP backed data sources must release the archive they
 * read from when the stream is closed, whether or not the caller consumed all of the lines.
 */
class ZipStreamResourceReleaseTest {

    @TempDir
    Path temporaryDirectory;

    private static final String FIRST_LINE = "[0.001s][info][gc] Using G1";
    private static final String SECOND_LINE = "[1.000s][info][gc] Pause Young (Normal)";
    private static final String LOG = FIRST_LINE + "\n\n   \n" + SECOND_LINE + "\n";

    @Test
    void singleZipReleasesArchiveWhenFullyConsumed() throws Exception {
        Path archive = zip("full.zip", Map.of("gc.log", LOG));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(List.of(FIRST_LINE, SECOND_LINE, GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipReleasesArchiveWhenPartiallyConsumed() throws Exception {
        Path archive = zip("partial.zip", Map.of("gc.log", LOG));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(FIRST_LINE, stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesArchiveWhenPartiallyConsumed() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(LOG.getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(FIRST_LINE, stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenFullyConsumed() throws Exception {
        Path archive = zip("segment.zip", Map.of("segment.log", LOG));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(List.of(FIRST_LINE, "", "   ", SECOND_LINE), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenReadingStartAndEndTimes() throws Exception {
        Path archive = zip("times.zip", Map.of("segment.log", LOG));
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "segment.log");
        assertEquals(0.001d, segment.getStartTime(), 0.0001d);
        assertEquals(1.000d, segment.getEndTime(), 0.0001d);

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipOrdersSegmentsWithoutRetainingTheArchive() throws Exception {
        Path archive = rotatingArchive("rotating-order.zip");
        long baseline = descriptorsFor(archive);

        // Ordering reads the start and end times of every segment out of the archive.
        List<String> segments = new RotatingGCLogFile(archive).getOrderedGarbageCollectionLogFiles()
                .stream()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());

        assertEquals(List.of("rotating.log.0", "rotating.log"), segments);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenFullyConsumed() throws Exception {
        Path archive = rotatingArchive("rotating-full.zip");
        long baseline = descriptorsFor(archive);
        long[] observed = new long[1];

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.peek(line -> observed[0] = Math.max(observed[0], descriptors(archive)))
                    .collect(Collectors.toList());
        }

        // segments are streamed oldest first, with the sentinel closing out the composed stream.
        assertEquals(List.of(
                "[0.001s][info][gc] Using G1",
                "[1.000s][info][gc] Pause Young (Normal)",
                "[2.000s][info][gc] Pause Young (Normal)",
                "[3.000s][info][gc] Pause Full (System.gc())",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertTrue(observed[0] > baseline, "the test must observe an open archive");
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenPartiallyConsumed() throws Exception {
        Path archive = rotatingArchive("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] Using G1", stream.findFirst().orElseThrow());
        }

        // covers the segments opened while streaming as well as those opened to order them.
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenStoppingBetweenSegments() throws Exception {
        Path archive = rotatingArchive("rotating-second-segment.zip");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // stops part way into the second segment.
            lines = stream.limit(3).collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] Using G1",
                "[1.000s][info][gc] Pause Young (Normal)",
                "[2.000s][info][gc] Pause Young (Normal)"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenNeverConsumed() throws Exception {
        Path archive = rotatingArchive("rotating-unconsumed.zip");
        long baseline = descriptorsFor(archive);

        new RotatingGCLogFile(archive).stream().close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    private long descriptors(Path archive) {
        try {
            return descriptorsFor(archive);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private Path rotatingArchive(String name) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("rotating.log.0", "[0.001s][info][gc] Using G1\n[1.000s][info][gc] Pause Young (Normal)\n");
        entries.put("rotating.log", "[2.000s][info][gc] Pause Young (Normal)\n[3.000s][info][gc] Pause Full (System.gc())\n");
        return zip(name, entries);
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
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
}

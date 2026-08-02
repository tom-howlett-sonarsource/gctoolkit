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
 * Every archive backed {@link Stream} must release its file handles when it is closed,
 * whether it was drained or abandoned part way through, and it must do so without
 * changing the lines, the segment ordering, or the end of data sentinel it produces.
 */
class ZipStreamLifecycleTest {

    private static final String SEGMENT_ZERO = ""
            + "[0.001s][info][gc] segment zero first\n"
            + "   \n"
            + "[1.000s][info][gc] segment zero last   \n";

    private static final String CURRENT_SEGMENT = ""
            + "[2.000s][info][gc] current first\n"
            + "[3.000s][info][gc] current last\n";

    private static final List<String> ROTATING_LINES = List.of(
            "[0.001s][info][gc] segment zero first",
            "[1.000s][info][gc] segment zero last",
            "[2.000s][info][gc] current first",
            "[3.000s][info][gc] current last",
            GCLogFile.END_OF_DATA_SENTINEL);

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipReleasesArchiveWhenFullyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("single-full.zip", Map.of("gc.log", SEGMENT_ZERO));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] segment zero first",
                "[1.000s][info][gc] segment zero last",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipReleasesArchiveWhenClosedTwice() throws Exception {
        Path archive = zip("single-twice.zip", Map.of("gc.log", SEGMENT_ZERO));
        long baseline = descriptorsFor(archive);

        Stream<String> stream = new SingleGCLogFile(archive).stream();
        assertEquals("[0.001s][info][gc] segment zero first", stream.findFirst().orElseThrow());
        stream.close();
        stream.close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesFileAfterPartialConsumption() throws Exception {
        Path archive = gzip("single.log.gz", SEGMENT_ZERO);
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] segment zero first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open file");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesLinesAndReleasesArchive() throws Exception {
        Path archive = zip("segment-full.zip", Map.of("gc.log", SEGMENT_ZERO));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // The segment stream is the raw contents of the entry: untrimmed, sentinel free.
        assertEquals(List.of(
                "[0.001s][info][gc] segment zero first",
                "   ",
                "[1.000s][info][gc] segment zero last   "), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenTimesAreComputed() throws Exception {
        Path archive = zip("segment-times.zip", Map.of("gc.log", SEGMENT_ZERO));
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(0.001d, segment.getStartTime(), 1e-9);
        assertEquals(1.000d, segment.getEndTime(), 1e-9);

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderingAndSentinel() throws Exception {
        Path archive = rotatingArchive("rotating-order.zip");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(ROTATING_LINES, lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveAfterPartialConsumptionOfFirstSegment() throws Exception {
        Path archive = rotatingArchive("rotating-first.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals(ROTATING_LINES.get(0), stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesEverySegmentWhenAbandonedInALaterSegment() throws Exception {
        Path archive = rotatingArchive("rotating-later.zip");
        long baseline = descriptorsFor(archive);

        // Reading three lines reaches into the second segment, so both segments have to
        // be released by the time the partially consumed stream has been closed.
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals(ROTATING_LINES.subList(0, 3), stream.limit(3).collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive(String name) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", CURRENT_SEGMENT);
        entries.put("gc.log.0", SEGMENT_ZERO);
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

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
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

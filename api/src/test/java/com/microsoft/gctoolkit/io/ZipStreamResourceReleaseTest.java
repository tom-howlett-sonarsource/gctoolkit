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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Every archive handle opened to back a {@code Stream<String>} has to be released when that
 * stream is closed, including when only part of it has been consumed. These tests count the
 * file descriptors that /proc reports against the archive, so they only run where /proc is
 * available.
 */
class ZipStreamResourceReleaseTest {

    private static final Path PROC_SELF_FD = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    // ---------- SingleGCLogFile ----------

    @Test
    void singleZipReleasesArchiveWhenFullyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("full.zip", entry("gc.log", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipReleasesArchiveWhenStreamIsNeverRead() throws Exception {
        Path archive = zip("unread.zip", entry("gc.log", "[0.001s][info][gc] first\n"));
        long baseline = descriptorsFor(archive);

        Stream<String> stream = new SingleGCLogFile(archive).stream();
        assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        stream.close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void closingSingleZipStreamTwiceIsHarmless() throws Exception {
        Path archive = zip("twice.zip", entry("gc.log", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n"));
        long baseline = descriptorsFor(archive);

        Stream<String> stream = new SingleGCLogFile(archive).stream();
        assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
        stream.close();
        stream.close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipPreservesLineContentsAndSentinel() throws Exception {
        Path archive = zip("contents.zip",
                entry("gc.log", "  [0.001s][info][gc] leading and trailing  \n\n   \n[0.002s][info][gc] after blanks\n"));

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(
                    List.of("[0.001s][info][gc] leading and trailing",
                            "[0.002s][info][gc] after blanks",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    stream.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGZipReleasesFileWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open file");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ---------- GCLogFileZipSegment ----------

    @Test
    void zipSegmentReleasesArchiveWhenFullyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("segments.zip",
                entry("gc.log.0", "[0.001s][info][gc] zero\n"),
                entry("gc.log.1", "[1.001s][info][gc] one\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.1").stream()) {
            assertEquals(List.of("[1.001s][info][gc] one"), stream.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentStartAndEndTimesDoNotLeakTheArchive() throws Exception {
        Path archive = zip("times.zip", entry("gc.log", "[1.500s][info][gc] first\n[9.250s][info][gc] last\n"));
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.5d, segment.getStartTime(), 0.0001d);
        assertEquals(9.25d, segment.getEndTime(), 0.0001d);

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithUnknownEntryReleasesTheArchive() throws Exception {
        Path archive = zip("missing.zip", entry("gc.log", "[0.001s][info][gc] first\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "absent.log").stream()) {
            assertEquals(List.of(), stream.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ---------- RotatingGCLogFile ----------

    @Test
    void rotatingZipReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = rotatingArchive("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        // Stop after the first line, so the later segments are never opened and the composed
        // stream is abandoned part way through the segment list.
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> lines = stream.iterator();
            assertEquals("[0.001s][info][gc] zero-a", lines.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenFullyConsumedStreamIsClosed() throws Exception {
        Path archive = rotatingArchive("rotating-full.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertTrue(stream.count() > 0);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenStreamIsNeverRead() throws Exception {
        Path archive = rotatingArchive("rotating-unread.zip");
        long baseline = descriptorsFor(archive);

        new RotatingGCLogFile(archive).stream().close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingArchive("rotating-order.zip");
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        List<String> expected = new ArrayList<>();
        for (LogFileSegment segment : logFile.getOrderedGarbageCollectionLogFiles()) {
            try (Stream<String> lines = segment.stream()) {
                lines.map(String::trim).filter(line -> !line.isEmpty()).forEach(expected::add);
            }
        }
        expected.add(GCLogFile.END_OF_DATA_SENTINEL);

        List<String> actual;
        try (Stream<String> stream = logFile.stream()) {
            actual = stream.collect(Collectors.toList());
        }

        assertEquals(expected, actual);
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, actual.get(actual.size() - 1));
        assertEquals(1, actual.stream().filter(GCLogFile.END_OF_DATA_SENTINEL::equals).count());
    }

    @Test
    void rotatingDirectoryReleasesSegmentsWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve("rotating"));
        Path current = write(directory.resolve("gc.log"), "[2.001s][info][gc] current-a\n[2.002s][info][gc] current-b\n");
        Path zero = write(directory.resolve("gc.log.0"), "[0.001s][info][gc] zero-a\n[0.002s][info][gc] zero-b\n");
        long baseline = descriptorsFor(current) + descriptorsFor(zero);

        try (Stream<String> stream = new RotatingGCLogFile(directory).stream()) {
            Iterator<String> lines = stream.iterator();
            assertFalse(lines.next().isEmpty());
        }

        assertEquals(baseline, descriptorsFor(current) + descriptorsFor(zero));
    }

    // ---------- helpers ----------

    /**
     * A rotating archive whose segments have disjoint, increasing time ranges so that the
     * metadata considers them contiguous.
     */
    private Path rotatingArchive(String name) throws IOException {
        return zip(name,
                entry("gc.log.0", "[0.001s][info][gc] zero-a\n[0.500s][info][gc] zero-b\n"),
                entry("gc.log.1", "[1.001s][info][gc] one-a\n[1.500s][info][gc] one-b\n"),
                entry("gc.log", "[2.001s][info][gc] current-a\n[2.500s][info][gc] current-b\n"));
    }

    private Map.Entry<String, String> entry(String name, String contents) {
        return Map.entry(name, contents);
    }

    @SafeVarargs
    private Path zip(String name, Map.Entry<String, String>... entries) throws IOException {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries)
            ordered.put(entry.getKey(), entry.getValue());

        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : ordered.entrySet()) {
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

    private Path write(Path path, String contents) throws IOException {
        Files.writeString(path, contents);
        return path;
    }

    private long descriptorsFor(Path file) throws IOException {
        assumeTrue(Files.isDirectory(PROC_SELF_FD), "descriptor counting requires /proc/self/fd");
        Path expected = file.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(PROC_SELF_FD)) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString()))
                        count++;
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

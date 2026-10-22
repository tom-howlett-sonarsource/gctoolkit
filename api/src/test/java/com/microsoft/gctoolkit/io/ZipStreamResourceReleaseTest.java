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

import static com.microsoft.gctoolkit.io.GCLogFile.END_OF_DATA_SENTINEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that ZIP/GZIP backed GC log streams release every underlying archive descriptor
 * when the returned {@link Stream} is closed, including after only partial consumption, while
 * preserving line contents, rotating-segment ordering and the end-of-data sentinel.
 *
 * These tests are additive and do not touch the supplied visible tests or acceptance metadata.
 */
class ZipStreamResourceReleaseTest {

    @TempDir
    Path temporaryDirectory;

    // ---- SingleGCLogFile (zip) ---------------------------------------------------------------

    @Test
    void singleZipFullyConsumedPreservesContentAndSentinel() throws Exception {
        Path archive = zip("single-full.zip", "gc.log", "[0.001s][info][gc] first\n\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertLinesMatch(List.of("[0.001s][info][gc] first", "second", END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive), "archive descriptors must be released after full consumption");
    }

    @Test
    void singleZipClosedAfterPartialConsumptionReleasesArchive() throws Exception {
        Path archive = zip("single-partial.zip", "gc.log", "[0.001s][info][gc] first\nsecond\nthird\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ---- SingleGCLogFile (gzip) --------------------------------------------------------------

    @Test
    void singleGZipFullyConsumedPreservesContentAndSentinel() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertLinesMatch(List.of("[0.001s][info][gc] first", "second", END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipClosedAfterPartialConsumptionReleasesArchive() throws Exception {
        Path archive = gzip("single-partial.log.gz", "[0.001s][info][gc] first\nsecond\nthird\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ---- GCLogFileZipSegment -----------------------------------------------------------------

    @Test
    void zipSegmentFullyConsumedPreservesContent() throws Exception {
        Path archive = zip("segment-full.zip", "segment.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // GCLogFileZipSegment streams the raw entry; the segment itself does not add the sentinel.
        assertLinesMatch(List.of("[0.001s][info][gc] first", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosedAfterPartialConsumptionReleasesArchive() throws Exception {
        Path archive = zip("segment-partial.zip", "segment.log", "[0.001s][info][gc] first\nsecond\nthird\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    // ---- RotatingGCLogFile (composed stream across zip entries) ------------------------------

    @Test
    void rotatingComposedStreamPreservesOrderingContentAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<LogFileSegment> ordered;
        List<String> composed;
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        ordered = logFile.getOrderedGarbageCollectionLogFiles();
        try (Stream<String> stream = logFile.stream()) {
            composed = stream.collect(Collectors.toList());
        }

        // Ordering: oldest segment first, current (newest) last.
        assertLinesMatch(List.of("rotating.log.0", "rotating.log.1", "rotating.log"),
                ordered.stream().map(LogFileSegment::getSegmentName).collect(Collectors.toList()));

        // Content follows the segment ordering and terminates with the sentinel exactly once.
        assertLinesMatch(List.of(
                "[0.001s][info][gc] s0 a",
                "[0.002s][info][gc] s0 b",
                "[1.001s][info][gc] s1 a",
                "[1.002s][info][gc] s1 b",
                "[2.001s][info][gc] current a",
                "[2.002s][info][gc] current b",
                END_OF_DATA_SENTINEL), composed);
    }

    @Test
    void rotatingComposedStreamClosedAfterPartialConsumptionReleasesArchive() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        // Partially consume the composed stream: read the first line of the first segment only,
        // leaving the remaining segments unread, then close the stream. flatMap closes each inner
        // segment stream as it advances, and the registered close handlers release the archive, so
        // no descriptor must survive the close.
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] s0 a", stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive), "composed rotating stream must release the archive on close");
    }

    // ---- helpers -----------------------------------------------------------------------------

    /**
     * Builds a rotating log held in a single zip: two numbered segments plus the current
     * (unnumbered) segment. Timestamps increase across segments so that the metadata orders
     * them oldest-first with the current segment last.
     */
    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "rotating.log.0", "[0.001s][info][gc] s0 a\n[0.002s][info][gc] s0 b\n");
            writeEntry(output, "rotating.log.1", "[1.001s][info][gc] s1 a\n[1.002s][info][gc] s1 b\n");
            writeEntry(output, "rotating.log", "[2.001s][info][gc] current a\n[2.002s][info][gc] current b\n");
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, entryName, contents);
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

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that ZIP-backed GC log streams release every underlying archive resource once the
 * returned {@link Stream} is closed, while preserving line contents, segment ordering and the
 * end-of-data sentinel. These tests are additive; the supplied visible tests are untouched.
 */
class ZipStreamResourceReleaseTest {

    @TempDir
    Path temporaryDirectory;

    // A rotating archive shipped in the repository: entries gc.log.0 (older) and gc.log.1.current.
    private static final Path ROTATING_ZIP = Path.of("..", "gclogs", "streaming", "rotating.zip");

    @Test
    void singleZipReleasesArchiveAndPreservesContentAndSentinel() throws Exception {
        Path archive = zip("single.zip", "gc.log", "[0.001s][info][gc] first\n\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive), "archive must be released after full consumption");
    }

    @Test
    void singleZipReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("single-partial.zip", "gc.log", "[0.001s][info][gc] first\nsecond\nthird\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveAndPreservesContent() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // A segment stream carries the raw lines; the sentinel is appended by the owning log file.
        assertEquals(List.of("[0.001s][info][gc] first", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("segment-partial.zip", "segment.log", "[0.001s][info][gc] first\nsecond\nthird\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipStreamPreservesOrderingContentsAndSentinel() throws Exception {
        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(ROTATING_ZIP).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // Segments are emitted oldest-first: gc.log.0 then gc.log.1.current.
        assertTrue(lines.get(0).startsWith("Java HotSpot(TM) 64-Bit Server VM"),
                "oldest segment (gc.log.0) must stream first");

        int banner = lines.indexOf("Java HotSpot(TM) 64-Bit Server VM (24.80-b11) for linux-amd64 JRE (1.7.0_80-b15), "
                + "built on Apr 10 2015 19:53:14 by \"java_re\" with gcc 4.3.0 20080428 (Red Hat 4.3.0-8)");
        int secondSegmentHeader = lines.indexOf("2016-08-30 20:35:29 GC log file created /var/log/swish/gc.log.1");
        assertTrue(banner >= 0, "content of the first segment must be present");
        assertTrue(secondSegmentHeader > banner, "second segment content must follow the first segment");

        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, lines.get(lines.size() - 1),
                "the composed stream must terminate with the end-of-data sentinel");
        assertEquals(1, lines.stream().filter(GCLogFile.END_OF_DATA_SENTINEL::equals).count(),
                "the sentinel must appear exactly once");
        assertFalse(lines.contains(""), "blank lines must be filtered out");
    }

    @Test
    void rotatingZipStreamReleasesArchiveAfterFullConsumption() throws Exception {
        Path archive = ROTATING_ZIP.toRealPath();
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(ROTATING_ZIP).stream()) {
            long consumed = stream.count();
            assertTrue(consumed > 0);
        }

        assertEquals(baseline, descriptorsFor(archive), "every segment archive must be released");
    }

    @Test
    void rotatingZipStreamReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = ROTATING_ZIP.toRealPath();
        long baseline = descriptorsFor(archive);

        // Consume only a short prefix (entirely within the first segment) and then close the
        // composed stream. The flatMap that composes the segments hands control back to the
        // caller only between segments, so the regression this guards against is a segment
        // archive that was opened while serving the prefix but never released on close.
        try (Stream<String> stream = new RotatingGCLogFile(ROTATING_ZIP).stream()) {
            List<String> prefix = stream.limit(3).collect(Collectors.toList());
            assertEquals(3, prefix.size());
        }

        assertEquals(baseline, descriptorsFor(archive),
                "closing a partially consumed composed stream must release every opened archive");
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
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

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional lifecycle coverage for ZIP-backed GC log streams, complementing the supplied
 * {@code VisibleZipStreamResourceLifecycleTest}. These tests assert that closing the returned
 * {@code Stream<String>} releases every underlying archive descriptor while preserving the line
 * contents, rotating-segment ordering, and the end-of-data sentinel.
 */
class ZipStreamResourceReleaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipReleasesArchiveWhenFullyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("full.zip", "gc.log", "[0.001s][info][gc] first\n\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        // Blank lines are dropped, remaining lines are trimmed, and the sentinel is appended last.
        assertEquals(List.of("[0.001s][info][gc] first", "second", END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive), "archive descriptor must be released on close");
    }

    @Test
    void segmentReleasesArchiveWhenFullyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("seg.zip", "segment.log", "[0.001s][info][gc] alpha\nbeta\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        // A raw segment stream preserves its contents verbatim and carries no sentinel.
        assertEquals(List.of("[0.001s][info][gc] alpha", "beta"), lines);
        assertEquals(baseline, descriptorsFor(archive), "archive descriptor must be released on close");
    }

    @Test
    void rotatingZipReleasesAllArchiveDescriptorsAfterPartialConsumption() throws Exception {
        // Two ordered segments in a single zip; .0 is the older segment, the base name is current.
        Path archive = zipMulti("rotating.zip",
                entry("rotating.log.0", "[1.000s][info][gc] old-start\n[2.000s][info][gc] old-end\n"),
                entry("rotating.log", "[3.000s][info][gc] new-start\n[4.000s][info][gc] new-end\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // Consume only the first line, then abandon the (partially consumed) stream.
            assertEquals("[1.000s][info][gc] old-start", stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive), "all archive descriptors must be released on close");
    }

    @Test
    void rotatingZipPreservesOrderingContentsAndSentinel() throws Exception {
        Path archive = zipMulti("ordered.zip",
                entry("ordered.log.0", "[1.000s][info][gc] old-start\n[2.000s][info][gc] old-end\n"),
                entry("ordered.log", "[3.000s][info][gc] new-start\n[4.000s][info][gc] new-end\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // Older segment first, newer segment second, end-of-data sentinel last.
        assertEquals(List.of(
                "[1.000s][info][gc] old-start",
                "[2.000s][info][gc] old-end",
                "[3.000s][info][gc] new-start",
                "[4.000s][info][gc] new-end",
                END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive), "all archive descriptors must be released on close");
    }

    private Entry entry(String name, String contents) {
        return new Entry(name, contents);
    }

    private static final class Entry {
        final String name;
        final String contents;
        Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        return zipMulti(name, new Entry(entryName, contents));
    }

    private Path zipMulti(String name, Entry... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Entry e : entries) {
                output.putNextEntry(new ZipEntry(e.name));
                output.write(e.contents.getBytes(StandardCharsets.UTF_8));
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

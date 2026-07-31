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
 * Additional coverage for the resource lifecycle of ZIP (and GZIP) backed GC log streams,
 * complementing {@link VisibleZipStreamResourceLifecycleTest}. These tests focus on full
 * consumption, line content/order/sentinel preservation, and the composed stream produced
 * by {@link RotatingGCLogFile} for a rotating log stored in a ZIP archive.
 */
class ZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("single.log.gz");
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write("[0.001s][info][gc] first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip(temporaryDirectory.resolve("full.zip"),
                Map.of("segment.log", "[0.001s][info][gc] first\nsecond\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderingContentAndSentinel() throws Exception {
        Path archive = zip(temporaryDirectory.resolve("rotating.zip"), rotatingEntries());

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);
        List<String> segmentOrder = rotatingGCLogFile.getOrderedGarbageCollectionLogFiles()
                .stream()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log.0", "gc.log"), segmentOrder);

        long baseline = descriptorsFor(archive);
        List<String> lines;
        try (Stream<String> stream = rotatingGCLogFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] oldest first",
                "[0.002s][info][gc] oldest second",
                "[10.001s][info][gc] current first",
                "[10.002s][info][gc] current second",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        // Unlike SingleGCLogFile/GCLogFileZipSegment, which hold one archive handle open for the
        // whole read, RotatingGCLogFile's composed stream opens one archive handle per segment via
        // flatMap. A short-circuiting terminal operation (e.g. findFirst) closes the in-flight
        // segment's handle synchronously as part of evaluating the operation, so there is no
        // observable "still open" window here — only the end state (no leaked handles) is asserted.
        Path archive = zip(temporaryDirectory.resolve("rotating-partial.zip"), rotatingEntries());

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);
        // Trigger ordering (and its own, already-closed archive handles) before taking the baseline.
        rotatingGCLogFile.getOrderedGarbageCollectionLogFiles();
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = rotatingGCLogFile.stream()) {
            assertEquals("[0.001s][info][gc] oldest first", stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Map<String, String> rotatingEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] oldest first\n[0.002s][info][gc] oldest second\n");
        entries.put("gc.log", "[10.001s][info][gc] current first\n[10.002s][info][gc] current second\n");
        return entries;
    }

    private Path zip(Path archive, Map<String, String> entries) throws IOException {
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

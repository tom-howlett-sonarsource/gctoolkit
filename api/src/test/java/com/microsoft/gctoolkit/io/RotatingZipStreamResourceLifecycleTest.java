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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the composed {@link Stream} returned by {@link RotatingGCLogFile#stream()} releases
 * the underlying ZIP archive resources when closed, including after partial consumption, while
 * preserving line contents, segment ordering, and the end-of-data sentinel. These tests live in a
 * separate file from the supplied visible tests, as required.
 */
class RotatingZipStreamResourceLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip", new String[][]{
                {"gc.log", "[0.100s][info][gc] current-a\n[0.200s][info][gc] current-b\n"},
                {"gc.log.0", "[0.001s][info][gc] older-a\n[0.002s][info][gc] older-b\n"},
        });
        long baseline = descriptorsFor(archive);

        // Abandon the stream after reading only the first line.
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] older-a", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive),
                "closing a partially consumed rotating stream must release every archive descriptor");
    }

    @Test
    void rotatingZipReleasesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip("rotating-full.zip", new String[][]{
                {"gc.log", "[0.100s][info][gc] only-line\n"},
        });
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals("[0.100s][info][gc] only-line", lines.get(0));
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, lines.get(lines.size() - 1),
                "the end-of-data sentinel must terminate the composed stream");
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderingContentsAndSentinel() throws Exception {
        Path archive = zip("rotating-ordered.zip", new String[][]{
                {"gc.log", "[0.100s][info][gc] current-a\n[0.200s][info][gc] current-b\n"},
                {"gc.log.0", "[0.001s][info][gc] older-a\n[0.002s][info][gc] older-b\n"},
        });

        // Expected = each segment, in the metadata's established rotating order, transformed the
        // same way the composed stream transforms its lines, followed by the sentinel.
        RotatingGCLogFile rotating = new RotatingGCLogFile(archive);
        List<String> expected = new ArrayList<>();
        for (LogFileSegment segment : rotating.getOrderedGarbageCollectionLogFiles()) {
            try (Stream<String> segmentLines = segment.stream()) {
                segmentLines.map(String::trim).filter(s -> s.length() > 0).forEach(expected::add);
            }
        }
        expected.add(GCLogFile.END_OF_DATA_SENTINEL);

        List<String> actual;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            actual = stream.collect(Collectors.toList());
        }

        assertEquals(expected, actual);
    }

    private Path zip(String name, String[][] entries) throws IOException {
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

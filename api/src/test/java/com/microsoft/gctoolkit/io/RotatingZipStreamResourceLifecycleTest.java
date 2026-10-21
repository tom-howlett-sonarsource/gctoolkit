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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.microsoft.gctoolkit.io.GCLogFile.END_OF_DATA_SENTINEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the composed {@link Stream} returned by {@link RotatingGCLogFile}, as well as the
 * individual zip-backed segment streams, release every underlying archive descriptor when the
 * returned stream is closed - including after only partial consumption.
 */
class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    // An entry currently being written to (gc.log) and an older rotated entry (gc.log.0). The older
    // segment carries the earlier timestamps, so RotatingGCLogFile orders it first.
    private static final String CURRENT_ENTRY = "gc.log";
    private static final String CURRENT_CONTENTS = "[2.000s][info][gc] third\n[3.000s][info][gc] fourth\n";
    private static final String ROTATED_ENTRY = "gc.log.0";
    private static final String ROTATED_CONTENTS = "[0.001s][info][gc] first\n[1.000s][info][gc] second\n";

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            // Only read the first line, leaving the composed stream (and its open archive) partially consumed.
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive), "closing the composed stream must release every archive descriptor");
    }

    @Test
    void rotatingZipPreservesContentsOrderingAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> actual;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            actual = lines.collect(java.util.stream.Collectors.toList());
        }

        List<String> expected = List.of(
                "[0.001s][info][gc] first",
                "[1.000s][info][gc] second",
                "[2.000s][info][gc] third",
                "[3.000s][info][gc] fourth",
                END_OF_DATA_SENTINEL);
        assertEquals(expected, actual, "rotating-segment ordering, line contents and the sentinel must be preserved");

        // The composed order must match the metadata segment order.
        List<String> fromMetadata = expectedFromMetadata(new RotatingGCLogFile(archive));
        assertEquals(fromMetadata, actual);
    }

    @Test
    void fullyConsumedRotatingZipReleasesArchive() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            lines.forEach(line -> {});
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private List<String> expectedFromMetadata(RotatingGCLogFile log) throws IOException {
        List<String> lines = new ArrayList<>();
        for (LogFileSegment segment : log.getOrderedGarbageCollectionLogFiles()) {
            try (Stream<String> stream = segment.stream()) {
                stream.filter(s -> !s.isBlank()).map(String::trim).filter(s -> !s.isEmpty()).forEach(lines::add);
            }
        }
        lines.add(END_OF_DATA_SENTINEL);
        return lines;
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            write(output, CURRENT_ENTRY, CURRENT_CONTENTS);
            write(output, ROTATED_ENTRY, ROTATED_CONTENTS);
        }
        return archive;
    }

    private void write(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
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

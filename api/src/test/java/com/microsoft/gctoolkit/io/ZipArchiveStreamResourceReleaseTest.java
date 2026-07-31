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
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipArchiveStreamResourceReleaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileGZipReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingGCLogFileZipReleasesArchiveAfterPartialConsumption() throws Exception {
        // Stream.flatMap (used to compose per-segment streams) closes each segment's
        // stream as soon as its contents have been pulled into the composed stream, so
        // the archive is already released by the time findFirst() returns - before the
        // composed stream itself is explicitly closed. That is a stronger guarantee than
        // "released no later than close()", so we assert release at both points.
        Path archive = rotatingZip("rotating.zip",
                "app.log.0", "[0.001s][info][gc] older-first\n[0.002s][info][gc] older-second\n",
                "app.log.1.current", "[0.010s][info][gc] current-first\n[0.011s][info][gc] current-second\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] older-first", lines.findFirst().orElseThrow());
            assertEquals(baseline, descriptorsFor(archive), "the segment archive must already be released");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGCLogFileZipPreservesLineContentAndEndOfDataSentinel() throws Exception {
        Path archive = zip("single-content.zip", "gc.log",
                "  [0.001s][info][gc] first  \n\n[0.002s][info][gc] second\n");

        List<String> actual;
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] first",
                "[0.002s][info][gc] second",
                GCLogFile.END_OF_DATA_SENTINEL
        ), actual);
    }

    @Test
    void zipSegmentStreamReturnsLinesVerbatim() throws Exception {
        Path archive = zip("segment-content.zip", "segment.log", "  first line  \nsecond line\n");

        List<String> actual;
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(List.of("  first line  ", "second line"), actual);
    }

    @Test
    void rotatingGCLogFileZipPreservesSegmentOrderingAndEndOfDataSentinel() throws Exception {
        Path archive = rotatingZip("rotating-content.zip",
                "app.log.0", "[0.001s][info][gc] older-first\n\n[0.002s][info][gc] older-second\n",
                "app.log.1.current", "[0.010s][info][gc] current-first\n[0.011s][info][gc] current-second\n");

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);
        List<LogFileSegment> orderedSegments = rotatingGCLogFile.getOrderedGarbageCollectionLogFiles();

        List<String> expected = orderedSegments.stream()
                .flatMap(segment -> {
                    try (Stream<String> lines = segment.stream()) {
                        return lines.collect(Collectors.toList()).stream();
                    }
                })
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        expected.add(GCLogFile.END_OF_DATA_SENTINEL);

        List<String> actual;
        try (Stream<String> lines = rotatingGCLogFile.stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(expected, actual);
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

    private Path rotatingZip(String name, String firstEntryName, String firstContents,
                              String secondEntryName, String secondContents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(firstEntryName));
            output.write(firstContents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry(secondEntryName));
            output.write(secondContents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
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

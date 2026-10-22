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

class ZipStreamResourceReleaseTest {

    private static final String FIRST = "[0.001s][info][gc] first";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesAllSegmentsAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip", rotatingEntries());
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertTrue(lines.iterator().hasNext());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesAllSegmentsAfterShortCircuit() throws Exception {
        Path archive = zip("rotating.zip", rotatingEntries());
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertTrue(lines.findFirst().isPresent());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = zip("rotating.zip", rotatingEntries());
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        List<String> expected = new ArrayList<>();
        for (LogFileSegment segment : logFile.getOrderedGarbageCollectionLogFiles())
            try (Stream<String> segmentLines = segment.stream()) {
                segmentLines.map(String::trim).filter(s -> !s.isEmpty()).forEach(expected::add);
            }
        expected.add(GCLogFile.END_OF_DATA_SENTINEL);

        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = logFile.stream()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
        assertEquals(7, expected.size());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipPreservesContentsAndSentinel() throws Exception {
        Path archive = zip("single.zip", Map.of("gc.log", FIRST + "\n\n  second  \n"));
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of(FIRST, "second", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGZipClosesFileAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("single.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write((FIRST + "\nsecond\n").getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(FIRST, lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimingQueriesDoNotLeakArchive() throws Exception {
        Path archive = zip("segment.zip", Map.of("segment.log", FIRST + "\n[0.500s][info][gc] last\n"));
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "segment.log");
        assertEquals(0.001d, segment.getStartTime());
        assertEquals(0.5d, segment.getEndTime());

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithMissingEntryReleasesArchive() throws Exception {
        Path archive = zip("segment.zip", Map.of("segment.log", FIRST + "\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private static Map<String, String> rotatingEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] zero-a\n[0.002s][info][gc] zero-b\n");
        entries.put("gc.log.1", "[1.001s][info][gc] one-a\n[1.002s][info][gc] one-b\n");
        entries.put("gc.log", "[2.001s][info][gc] current-a\n[2.002s][info][gc] current-b\n");
        return entries;
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private static long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).toString().replace(" (deleted)", "").equals(expected.toString()))
                        count++;
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

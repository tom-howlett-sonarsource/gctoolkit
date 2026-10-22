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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamResourceReleaseTest {

    private static final String OLDEST = "[1.000s][info][gc] oldest a\n  \n[2.000s][info][gc] oldest b\n";
    private static final String CURRENT = "[3.000s][info][gc] current a\n[4.000s][info][gc] current b\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipPreservesOrderingAndSentinelAndReleasesArchive() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        logFile.getMetaData().logFiles().count(); // resolve segment ordering before taking the baseline
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[1.000s][info][gc] oldest a", "[2.000s][info][gc] oldest b",
                "[3.000s][info][gc] current a", "[4.000s][info][gc] current b",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenPartiallyConsumedViaIterator() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        logFile.getMetaData().logFiles().count();
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = logFile.stream()) {
            Iterator<String> iterator = stream.iterator();
            assertEquals("[1.000s][info][gc] oldest a", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentOrderingQueriesReleaseArchive() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        assertEquals(3.0d, segment.getStartTime());
        assertEquals(4.0d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithMissingEntryIsEmptyAndReleasesArchive() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, stream.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipFullyConsumedPreservesContentAndReleasesArchive() throws Exception {
        Path archive = zip("single.zip", Map.of("gc.log", CURRENT));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[3.000s][info][gc] current a", "[4.000s][info][gc] current b",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesFileWhenPartiallyConsumed() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write(CURRENT.getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[3.000s][info][gc] current a", stream.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open file");
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", CURRENT);
        entries.put("gc.log.0", OLDEST);
        return zip("rotating.zip", entries);
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

    private static long descriptorsFor(Path file) throws IOException {
        String expected = file.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).toString().replace(" (deleted)", "").equals(expected))
                        count++;
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

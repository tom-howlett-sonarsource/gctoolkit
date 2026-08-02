// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.BeforeAll;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Archive backed log streams hold a file descriptor for as long as they are readable. These tests
 * assert that closing the returned {@code Stream<String>} releases that descriptor, whether the
 * stream was drained or only partially consumed, and that doing so does not disturb the line
 * contents, the rotating segment ordering, or the end of data sentinel.
 */
class ZipStreamResourceReleaseTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void requireDescriptorIntrospection() {
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS), "descriptor leak detection needs /proc/self/fd");
    }

    @Test
    void singleZipReleasesArchiveWhenDrainedStreamIsClosed() throws Exception {
        Path archive = zip("drained.zip", entries("gc.log", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("partial.zip", entries("gc.log", body(1, 500)));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(line(3), stream.skip(2).findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should still be open mid-read");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesFileWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = temporaryDirectory.resolve("partial.log.gz");
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(body(1, 500).getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(line(1), stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the gzip file should still be open mid-read");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesFileWhenTheHeaderCannotBeRead() throws Exception {
        Path corrupt = temporaryDirectory.resolve("corrupt.log.gz");
        // A valid gzip magic number followed by garbage: recognised as gzip, then fails to open.
        byte[] bytes = new byte[64];
        bytes[0] = (byte) 0x1f;
        bytes[1] = (byte) 0x8b;
        Files.write(corrupt, bytes);
        long baseline = descriptorsFor(corrupt);

        assertThrows(IOException.class, () -> new SingleGCLogFile(corrupt).stream());

        assertEquals(baseline, descriptorsFor(corrupt));
    }

    @Test
    void zipSegmentReleasesArchiveWhenDrainedStreamIsClosed() throws Exception {
        Path archive = zip("segments.zip", entries("gc.log.0", body(1, 4)));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(line(1), line(2), line(3), line(4)), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = zip("segment-partial.zip", entries("gc.log.0", body(1, 500)));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals(line(1), stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should still be open mid-read");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenComputingSegmentTimes() throws Exception {
        Path archive = zip("times.zip", entries("gc.log.0", body(1, 200)));
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");
        assertEquals(1.0d, segment.getStartTime(), 0.0001d);
        assertEquals(200.0d, segment.getEndTime(), 0.0001d);

        assertEquals(baseline, descriptorsFor(archive), "start/end time probes must not leak the archive");
    }

    @Test
    void zipSegmentSurvivesAMissingEntryWithoutLeaking() throws Exception {
        Path archive = zip("missing.zip", entries("gc.log.0", body(1, 2)));
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "absent.log").stream()) {
            assertEquals(List.of(), stream.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesEveryArchiveWhenDrainedStreamIsClosed() throws Exception {
        Path archive = rotatingArchive("rotating-drained.zip");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(rotatingExpectation(), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesEveryArchiveWhenShortCircuitedStreamIsClosed() throws Exception {
        Path archive = rotatingArchive("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // Stops inside the first segment; the later segments are never opened.
            assertEquals(line(1), stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchivesWhenAnIteratorIsAbandonedMidSegment() throws Exception {
        Path archive = rotatingArchive("rotating-iterator.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // Pull lines by hand and walk away part way through the second of three segments. flatMap
            // closes a segment it has drained or cancelled, but the composed stream's close handler is
            // what makes the release independent of when the caller stops pulling.
            Iterator<String> lines = stream.iterator();
            List<String> read = new ArrayList<>();
            for (int count = 0; count < 13 && lines.hasNext(); count++)
                read.add(lines.next());
            assertEquals(rotatingExpectation().subList(0, 13), read);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive(String name) throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("gc.log.0", body(1, 10));
        contents.put("gc.log.1", body(11, 20));
        contents.put("gc.log", body(21, 30));
        return zip(name, contents);
    }

    /** Oldest segment first, newest last, with the sentinel appended. */
    private List<String> rotatingExpectation() {
        List<String> expected = Stream.iterate(1, i -> i + 1)
                .limit(30)
                .map(ZipStreamResourceReleaseTest::line)
                .collect(Collectors.toList());
        expected.add(GCLogFile.END_OF_DATA_SENTINEL);
        return expected;
    }

    private static Map<String, String> entries(String entryName, String contents) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(entryName, contents);
        return entries;
    }

    private static String line(int index) {
        return "[" + index + ".000s][info][gc] GC(" + index + ") Pause Young";
    }

    private static String body(int firstIndex, int lastIndex) {
        StringBuilder contents = new StringBuilder();
        for (int index = firstIndex; index <= lastIndex; index++)
            contents.append(line(index)).append('\n');
        return contents.toString();
    }

    private Path zip(String name, Map<String, String> contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : contents.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    /** The number of open file descriptors this JVM holds against {@code archive}. */
    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(FILE_DESCRIPTORS)) {
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

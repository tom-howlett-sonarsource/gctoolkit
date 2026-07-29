// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compressed GC logs are read through streams that the caller is expected to close. These tests check
 * that closing the stream releases the archive underneath it, whether the stream was drained or
 * abandoned part way through, and that the lines delivered are unchanged.
 */
class ZipStreamResourceLifecycleTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    private static final String OLDER_SEGMENT_LINES =
            "[1.000s][info][gc] GC(0) Pause Young (Normal) 10M->5M(64M) 1.000ms\n" +
            "\n" +
            "[2.000s][info][gc] GC(1) Pause Young (Normal) 12M->6M(64M) 1.100ms\n";

    private static final String CURRENT_SEGMENT_LINES =
            "[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms\n" +
            "[4.000s][info][gc] GC(3) Pause Young (Normal) 16M->8M(64M) 1.300ms\n";

    @TempDir
    Path directory;

    @BeforeEach
    void onlyRunWhereOpenFilesCanBeCounted() {
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS), "counting open files requires /proc/self/fd");
    }

    @Test
    void singleZipLogReleasesArchiveWhenDrained() throws IOException {
        Path archive = zip("single.zip", entries("gc.log", CURRENT_SEGMENT_LINES));
        long openFiles = openFileCount(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                "[4.000s][info][gc] GC(3) Pause Young (Normal) 16M->8M(64M) 1.300ms",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void singleZipLogReleasesArchiveWhenPartiallyConsumed() throws IOException {
        Path archive = zip("partial.zip", entries("gc.log", CURRENT_SEGMENT_LINES));
        long openFiles = openFileCount(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                    stream.findFirst().orElseThrow(AssertionError::new));
            assertTrue(openFileCount(archive) > openFiles, "the archive should be open while it is being read");
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void singleGZipLogReleasesArchiveWhenPartiallyConsumed() throws IOException {
        Path archive = gzip("gc.log.gz", CURRENT_SEGMENT_LINES);
        long openFiles = openFileCount(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals("[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                    stream.findFirst().orElseThrow(AssertionError::new));
            assertTrue(openFileCount(archive) > openFiles, "the archive should be open while it is being read");
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenPartiallyConsumed() throws IOException {
        Path archive = zip("segment.zip", entries("gc.log", CURRENT_SEGMENT_LINES));
        long openFiles = openFileCount(archive);

        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                    stream.findFirst().orElseThrow(AssertionError::new));
            assertTrue(openFileCount(archive) > openFiles, "the archive should be open while it is being read");
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenDrained() throws IOException {
        Path archive = zip("drained.zip", entries("gc.log", OLDER_SEGMENT_LINES));
        long openFiles = openFileCount(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        List<String> lines;
        try (Stream<String> stream = segment.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // the segment streams the entry as it is, blank lines included.
        assertEquals(List.of(
                "[1.000s][info][gc] GC(0) Pause Young (Normal) 10M->5M(64M) 1.000ms",
                "",
                "[2.000s][info][gc] GC(1) Pause Young (Normal) 12M->6M(64M) 1.100ms"), lines);
        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenReadingItsTimeStamps() throws IOException {
        Path archive = zip("timestamps.zip", entries("gc.log", OLDER_SEGMENT_LINES));
        long openFiles = openFileCount(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        assertEquals(1.000d, segment.getStartTime(), 0.001d);
        assertEquals(2.000d, segment.getEndTime(), 0.001d);
        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void closingTwiceReleasesTheArchiveOnce() throws IOException {
        Path archive = zip("twice.zip", entries("gc.log", CURRENT_SEGMENT_LINES));
        long openFiles = openFileCount(archive);

        Stream<String> stream = new SingleGCLogFile(archive).stream();
        assertEquals("[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                stream.findFirst().orElseThrow(AssertionError::new));
        stream.close();
        stream.close();

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void rotatingZipLogReleasesEverySegmentWhenDrained() throws IOException {
        Path archive = rotatingArchive("rotating-drained.zip");
        long openFiles = openFileCount(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // oldest segment first, then the current segment, then the sentinel.
        assertEquals(List.of(
                "[1.000s][info][gc] GC(0) Pause Young (Normal) 10M->5M(64M) 1.000ms",
                "[2.000s][info][gc] GC(1) Pause Young (Normal) 12M->6M(64M) 1.100ms",
                "[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                "[4.000s][info][gc] GC(3) Pause Young (Normal) 16M->8M(64M) 1.300ms",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void rotatingZipLogReleasesEverySegmentWhenPartiallyConsumed() throws IOException {
        Path archive = rotatingArchive("rotating-partial.zip");
        long openFiles = openFileCount(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // stop inside the second segment, leaving the composed stream in mid flight.
            assertEquals(List.of(
                    "[1.000s][info][gc] GC(0) Pause Young (Normal) 10M->5M(64M) 1.000ms",
                    "[2.000s][info][gc] GC(1) Pause Young (Normal) 12M->6M(64M) 1.100ms",
                    "[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms"),
                    stream.limit(3).collect(Collectors.toList()));
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void rotatingZipLogReleasesEverySegmentWhenOnlyOneLineIsRead() throws IOException {
        Path archive = rotatingArchive("rotating-one-line.zip");
        long openFiles = openFileCount(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[1.000s][info][gc] GC(0) Pause Young (Normal) 10M->5M(64M) 1.000ms",
                    stream.findFirst().orElseThrow(AssertionError::new));
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void zipSegmentReleasesTheArchiveWhenItCannotBeRead() throws IOException {
        Path notAnArchive = Files.write(directory.resolve("gc.log"), CURRENT_SEGMENT_LINES.getBytes(StandardCharsets.UTF_8));
        long openFiles = openFileCount(notAnArchive);

        try (Stream<String> stream = new GCLogFileZipSegment(notAnArchive, "gc.log").stream()) {
            assertEquals(List.of(), stream.collect(Collectors.toList()));
        }

        assertEquals(openFiles, openFileCount(notAnArchive));
    }

    @Test
    void singleZipLogReleasesTheFileWhenTheFirstEntryCannotBeRead() throws IOException {
        // an entry header claiming a data descriptor on a stored entry, which the first read of the
        // entry rejects. The file is recognised as a Zip, so it is the Zip stream that fails.
        byte[] header = new byte[30];
        header[0] = 0x50;
        header[1] = 0x4b;
        header[2] = 0x03;
        header[3] = 0x04;
        header[6] = 0x08;
        Path archive = Files.write(directory.resolve("malformed.zip"), header);
        long openFiles = openFileCount(archive);

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void rotatingGZipLogIsUnsupportedAndYieldsOnlyTheSentinel() throws IOException {
        Path archive = gzip("rotating.log.gz", CURRENT_SEGMENT_LINES);
        long openFiles = openFileCount(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(GCLogFile.END_OF_DATA_SENTINEL), stream.collect(Collectors.toList()));
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void singleGZipLogReleasesTheFileWhenTheHeaderCannotBeRead() throws IOException {
        // the GZip magic bytes followed by an unusable header, so the file is read as GZip and then fails.
        Path archive = Files.write(directory.resolve("truncated.log.gz"), new byte[] {0x1f, (byte) 0x8b, 0x00, 0x00});
        long openFiles = openFileCount(archive);

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertEquals(openFiles, openFileCount(archive));
    }

    /**
     * The tests above conclude that nothing was left open from a file descriptor count. This checks that
     * an archive which really is left open is counted, so that those conclusions mean something.
     */
    @Test
    void anArchiveLeftOpenIsCounted() throws IOException {
        Path archive = zip("counted.zip", entries("gc.log", CURRENT_SEGMENT_LINES));
        long openFiles = openFileCount(archive);

        Stream<String> leaked = new GCLogFileZipSegment(archive, "gc.log").stream();
        try {
            assertEquals("[3.000s][info][gc] GC(2) Pause Young (Normal) 14M->7M(64M) 1.200ms",
                    leaked.findFirst().orElseThrow(AssertionError::new));
            assertTrue(openFileCount(archive) > openFiles, "an unclosed archive should be counted");
        } finally {
            leaked.close();
        }

        assertEquals(openFiles, openFileCount(archive));
    }

    @Test
    void aResourceThatCannotBeClosedDoesNotStopTheOthersFromBeingReleased() {
        List<String> released = new ArrayList<>();

        LineStreams.closeAll(List.<AutoCloseable>of(
                () -> {
                    released.add("first");
                    throw new IOException("this one cannot be closed");
                },
                () -> released.add("second")));

        assertEquals(List.of("first", "second"), released);
    }

    /**
     * An archive holding two rotating segments, the current one written last.
     */
    private Path rotatingArchive(String name) throws IOException {
        Map<String, String> segments = entries("gc.log.0", OLDER_SEGMENT_LINES);
        segments.put("gc.log", CURRENT_SEGMENT_LINES);
        return zip(name, segments);
    }

    private Map<String, String> entries(String name, String contents) {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put(name, contents);
        return entries;
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = directory.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return archive;
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = directory.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    /**
     * The number of open file descriptors pointing at {@code archive}. A descriptor that is never
     * closed keeps the archive in this list, which is how a leak shows up.
     */
    private long openFileCount(Path archive) throws IOException {
        String expected = archive.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(FILE_DESCRIPTORS)) {
            for (Path descriptor : descriptors) {
                try {
                    if (expected.equals(Files.readSymbolicLink(descriptor).toString()))
                        count++;
                } catch (IOException descriptorClosedWhileWeLooked) {
                    // a descriptor can be closed by another thread while /proc is being read.
                }
            }
        }
        return count;
    }
}

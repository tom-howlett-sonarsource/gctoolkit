// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The lines of an archived GC log are read through resources that the JVM will not reclaim
 * on its own. These tests pin down both halves of the contract: the lines a caller sees, and
 * the release of the archive when the caller closes the stream those lines came from.
 */
class ZipStreamResourceLifecycleTest {

    private static final String SENTINEL = GCLogFile.END_OF_DATA_SENTINEL;
    private static final Path OPEN_DESCRIPTORS = Path.of("/proc/self/fd");

    private static final String OLDEST_SEGMENT_NAME = "gc.log.0";
    private static final String CURRENT_SEGMENT_NAME = "gc.log";
    private static final String OLDEST_SEGMENT =
            "[0.001s][info][gc] Using G1\n" +
            "[1.000s][info][gc] Pause Young (Normal) 1M->1M(8M)\n";
    private static final String CURRENT_SEGMENT =
            "[2.000s][info][gc] Pause Young (Normal) 2M->2M(8M)\n" +
            "[3.000s][info][gc] Pause Full 3M->1M(8M)\n";

    @TempDir
    Path directory;

    @Test
    void singleZipStreamsTrimmedLinesFollowedByTheSentinel() throws IOException {
        Path archive = zip("single.zip", entry("gc.log", "[0.001s][info][gc] Using G1\n\n   [1.000s][info][gc] Pause Full  \n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(
                    List.of("[0.001s][info][gc] Using G1", "[1.000s][info][gc] Pause Full", SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGZipStreamsTrimmedLinesFollowedByTheSentinel() throws IOException {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] Using G1\n\n   [1.000s][info][gc] Pause Full  \n");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(
                    List.of("[0.001s][info][gc] Using G1", "[1.000s][info][gc] Pause Full", SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentStreamsTheLinesOfItsOwnEntry() throws IOException {
        Path archive = zip("rotating.zip", entry(OLDEST_SEGMENT_NAME, OLDEST_SEGMENT), entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, CURRENT_SEGMENT_NAME).stream()) {
            assertEquals(
                    List.of("[2.000s][info][gc] Pause Young (Normal) 2M->2M(8M)", "[3.000s][info][gc] Pause Full 3M->1M(8M)"),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentStreamsNothingWhenTheEntryIsMissing() throws IOException {
        Path archive = zip("rotating.zip", entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "absent.log").stream()) {
            assertEquals(List.of(), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipStreamsSegmentsInOrderFollowedByTheSentinel() throws IOException {
        Path archive = zip("rotating.zip", entry(OLDEST_SEGMENT_NAME, OLDEST_SEGMENT), entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(
                    List.of("[0.001s][info][gc] Using G1",
                            "[1.000s][info][gc] Pause Young (Normal) 1M->1M(8M)",
                            "[2.000s][info][gc] Pause Young (Normal) 2M->2M(8M)",
                            "[3.000s][info][gc] Pause Full 3M->1M(8M)",
                            SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    /**
     * An unreleased archive is only visible from outside the classes under test as a file
     * descriptor this JVM still holds, so these tests need a {@code /proc} to look at.
     */
    @Nested
    class ArchiveRelease {

        @BeforeEach
        void descriptorsMustBeObservable() {
            assumeTrue(Files.isDirectory(OPEN_DESCRIPTORS), OPEN_DESCRIPTORS + " is needed to observe an open archive");
        }

        @Test
        void singleZipIsReleasedWhenAFullyConsumedStreamIsClosed() throws IOException {
            Path archive = zip("single.zip", entry("gc.log", CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
                assertEquals(3, lines.count());
            }

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void singleZipIsReleasedWhenAPartiallyConsumedStreamIsClosed() throws IOException {
            Path archive = zip("single.zip", entry("gc.log", CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
                assertEquals("[2.000s][info][gc] Pause Young (Normal) 2M->2M(8M)", lines.findFirst().orElseThrow());
                assertTrue(descriptorsFor(archive) > baseline, "the archive should still be open part way through");
            }

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void singleGZipIsReleasedWhenAPartiallyConsumedStreamIsClosed() throws IOException {
            Path archive = gzip("single.log.gz", CURRENT_SEGMENT);
            long baseline = descriptorsFor(archive);

            try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
                assertEquals("[2.000s][info][gc] Pause Young (Normal) 2M->2M(8M)", lines.findFirst().orElseThrow());
                assertTrue(descriptorsFor(archive) > baseline, "the archive should still be open part way through");
            }

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void aZipThatCannotBeOpenedIsReleasedBeforeTheFailureReachesTheCaller() throws IOException {
            Path archive = bytes("corrupt.zip", unreadableZipEntry());
            long baseline = descriptorsFor(archive);

            SingleGCLogFile log = new SingleGCLogFile(archive);
            assertThrows(IOException.class, log::stream);

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void aGZipThatCannotBeOpenedIsReleasedBeforeTheFailureReachesTheCaller() throws IOException {
            Path archive = bytes("corrupt.log.gz", unreadableGZipMember());
            long baseline = descriptorsFor(archive);

            SingleGCLogFile log = new SingleGCLogFile(archive);
            assertThrows(IOException.class, log::stream);

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void zipSegmentIsReleasedWhenAPartiallyConsumedStreamIsClosed() throws IOException {
            Path archive = zip("rotating.zip", entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            try (Stream<String> lines = new GCLogFileZipSegment(archive, CURRENT_SEGMENT_NAME).stream()) {
                assertEquals("[2.000s][info][gc] Pause Young (Normal) 2M->2M(8M)", lines.findFirst().orElseThrow());
                assertTrue(descriptorsFor(archive) > baseline, "the archive should still be open part way through");
            }

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void zipSegmentIsReleasedWhenTheEntryIsMissing() throws IOException {
            Path archive = zip("rotating.zip", entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            new GCLogFileZipSegment(archive, "absent.log").stream().close();

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void zipSegmentIsReleasedWhenItsTimesAreRead() throws IOException {
            Path archive = zip("rotating.zip", entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, CURRENT_SEGMENT_NAME);
            assertEquals(2.0d, segment.getStartTime());
            assertEquals(3.0d, segment.getEndTime());

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void rotatingZipIsReleasedWhenItsSegmentsAreOrdered() throws IOException {
            Path archive = zip("rotating.zip", entry(OLDEST_SEGMENT_NAME, OLDEST_SEGMENT), entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            assertEquals(2, new RotatingGCLogFile(archive).getOrderedGarbageCollectionLogFiles().size());

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void rotatingZipIsReleasedWhenAFullyConsumedStreamIsClosed() throws IOException {
            Path archive = zip("rotating.zip", entry(OLDEST_SEGMENT_NAME, OLDEST_SEGMENT), entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
                assertEquals(5, lines.count());
            }

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void rotatingZipIsReleasedWhenAPartiallyConsumedStreamIsClosed() throws IOException {
            Path archive = zip("rotating.zip", entry(OLDEST_SEGMENT_NAME, OLDEST_SEGMENT), entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
                assertEquals("[0.001s][info][gc] Using G1", lines.findFirst().orElseThrow());
            }

            assertEquals(baseline, descriptorsFor(archive));
        }

        @Test
        void rotatingZipIsReleasedWhenAStreamIsClosedWithoutBeingRead() throws IOException {
            Path archive = zip("rotating.zip", entry(OLDEST_SEGMENT_NAME, OLDEST_SEGMENT), entry(CURRENT_SEGMENT_NAME, CURRENT_SEGMENT));
            long baseline = descriptorsFor(archive);

            new RotatingGCLogFile(archive).stream().close();

            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    private Path zip(String name, Entry... entries) throws IOException {
        Path archive = directory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Entry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = directory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    private Path bytes(String name, byte[] contents) throws IOException {
        Path archive = directory.resolve(name);
        Files.write(archive, contents);
        return archive;
    }

    /**
     * The magic number of a zip file followed by the local header of an entry that
     * {@link java.util.zip.ZipInputStream} refuses to read, which is one that declares
     * itself encrypted. The header fields are little endian: the entry signature, the
     * version needed to extract it, then the general purpose flags.
     */
    private static byte[] unreadableZipEntry() {
        byte[] header = new byte[30];
        header[0] = 0x50;
        header[1] = 0x4B;
        header[2] = 0x03;
        header[3] = 0x04;
        header[4] = 0x0A;
        header[6] = 0x01;
        return header;
    }

    /**
     * The magic number of a gzip file followed by bytes that are not a gzip member, which
     * {@link java.util.zip.GZIPInputStream} refuses to read.
     */
    private static byte[] unreadableGZipMember() {
        return new byte[] {0x1F, (byte) 0x8B, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
    }

    private static Entry entry(String name, String contents) {
        return new Entry(name, contents);
    }

    /**
     * The number of descriptors this JVM holds on {@code archive}.
     */
    private static long descriptorsFor(Path archive) throws IOException {
        String expected = archive.toRealPath().toString();
        long open = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(OPEN_DESCRIPTORS)) {
            for (Path descriptor : descriptors) {
                try {
                    if (expected.equals(Files.readSymbolicLink(descriptor).toString()))
                        open++;
                } catch (IOException disappeared) {
                    // A descriptor can be closed while /proc is being walked.
                }
            }
        }
        return open;
    }

    private static class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The streams handed out by the ZIP and GZIP backed data sources must release the archives they read
 * from when the caller closes the stream, whether or not the stream has been fully consumed. The
 * tests that count open file descriptors read {@code /proc/self/fd} and so are limited to Linux.
 */
class ZipStreamResourceLifecycleTest {

    private static final String FIRST_SEGMENT = "[0.001s][info][gc] segment one, line one\n"
            + "\n"
            + "   [0.002s][info][gc] segment one, line two   \n"
            + "[0.003s][info][gc] segment one, line three\n";
    private static final String SECOND_SEGMENT = "[10.001s][info][gc] segment two, line one\n"
            + "[10.002s][info][gc] segment two, line two\n";
    private static final String CURRENT_SEGMENT = "[20.001s][info][gc] current segment, line one\n"
            + "[20.002s][info][gc] current segment, line two\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path archive = zip("single.zip", entry("gc.log", FIRST_SEGMENT));

        assertEquals(
                List.of("[0.001s][info][gc] segment one, line one",
                        "[0.002s][info][gc] segment one, line two",
                        "[0.003s][info][gc] segment one, line three",
                        GCLogFile.END_OF_DATA_SENTINEL),
                lines(new SingleGCLogFile(archive)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void singleZipStreamReleasesArchiveWhenFullyConsumedStreamIsClosed() throws IOException {
        Path archive = zip("single.zip", entry("gc.log", FIRST_SEGMENT));
        long baseline = openDescriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(4L, lines.count());
            assertTrue(openDescriptorsFor(archive) > baseline, "the archive should be held open while streaming");
        }

        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void singleZipStreamReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = zip("single.zip", entry("gc.log", FIRST_SEGMENT));
        long baseline = openDescriptorsFor(archive);

        Stream<String> lines = new SingleGCLogFile(archive).stream();
        assertEquals("[0.001s][info][gc] segment one, line one", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > baseline, "the archive should be held open while streaming");

        lines.close();
        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    void singleGZipStreamPreservesLinesAndSentinel() throws IOException {
        Path archive = gzip("gc.log.gz", FIRST_SEGMENT);

        assertEquals(
                List.of("[0.001s][info][gc] segment one, line one",
                        "[0.002s][info][gc] segment one, line two",
                        "[0.003s][info][gc] segment one, line three",
                        GCLogFile.END_OF_DATA_SENTINEL),
                lines(new SingleGCLogFile(archive)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void singleGZipStreamReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = gzip("gc.log.gz", FIRST_SEGMENT);
        long baseline = openDescriptorsFor(archive);

        Stream<String> lines = new SingleGCLogFile(archive).stream();
        assertEquals("[0.001s][info][gc] segment one, line one", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > baseline, "the archive should be held open while streaming");

        lines.close();
        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    void zipSegmentStreamPreservesLines() throws IOException {
        Path archive = zip("rotating.zip", entry("gc.log.0", FIRST_SEGMENT), entry("gc.log.1", SECOND_SEGMENT));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.1").stream()) {
            assertEquals(
                    List.of("[10.001s][info][gc] segment two, line one",
                            "[10.002s][info][gc] segment two, line two"),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void zipSegmentStreamReleasesArchiveWhenFullyConsumedStreamIsClosed() throws IOException {
        Path archive = zip("rotating.zip", entry("gc.log.0", FIRST_SEGMENT), entry("gc.log.1", SECOND_SEGMENT));
        long baseline = openDescriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals(4L, lines.count());
            assertTrue(openDescriptorsFor(archive) > baseline, "the archive should be held open while streaming");
        }

        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void zipSegmentStreamReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = zip("rotating.zip", entry("gc.log.0", FIRST_SEGMENT), entry("gc.log.1", SECOND_SEGMENT));
        long baseline = openDescriptorsFor(archive);

        Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream();
        assertEquals("[0.001s][info][gc] segment one, line one", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > baseline, "the archive should be held open while streaming");

        lines.close();
        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void zipSegmentStreamOfUnknownEntryIsEmptyAndReleasesArchive() throws IOException {
        Path archive = zip("rotating.zip", entry("gc.log.0", FIRST_SEGMENT));
        long baseline = openDescriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "not.a.segment").stream()) {
            assertEquals(0L, lines.count());
        }

        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void zipSegmentTimesReleaseTheArchive() throws IOException {
        Path archive = zip("rotating.zip", entry("gc.log.1", SECOND_SEGMENT));
        long baseline = openDescriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.1");
        assertEquals(10.001d, segment.getStartTime());
        assertEquals(10.002d, segment.getEndTime());

        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderAndSentinel() throws IOException {
        Path archive = rotatingArchive();

        assertEquals(
                List.of("[0.001s][info][gc] segment one, line one",
                        "[0.002s][info][gc] segment one, line two",
                        "[0.003s][info][gc] segment one, line three",
                        "[10.001s][info][gc] segment two, line one",
                        "[10.002s][info][gc] segment two, line two",
                        "[20.001s][info][gc] current segment, line one",
                        "[20.002s][info][gc] current segment, line two",
                        GCLogFile.END_OF_DATA_SENTINEL),
                lines(new RotatingGCLogFile(archive)));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void rotatingZipStreamReleasesArchiveWhenFullyConsumedStreamIsClosed() throws IOException {
        Path archive = rotatingArchive();
        long baseline = openDescriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(8L, lines.count());
        }

        assertEquals(baseline, openDescriptorsFor(archive));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void rotatingZipStreamReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = rotatingArchive();
        long baseline = openDescriptorsFor(archive);

        Stream<String> lines = new RotatingGCLogFile(archive).stream();
        Iterator<String> iterator = lines.iterator();
        assertEquals("[0.001s][info][gc] segment one, line one", iterator.next());

        lines.close();
        assertEquals(baseline, openDescriptorsFor(archive), "closing the composed stream must release every segment");
    }

    /**
     * A rotating log held in a single archive. The current segment carries no index, the remaining
     * segments cover contiguous, earlier, spans of the JVM's life.
     */
    private Path rotatingArchive() throws IOException {
        return zip("rotating.zip",
                entry("gc.log", CURRENT_SEGMENT),
                entry("gc.log.0", FIRST_SEGMENT),
                entry("gc.log.1", SECOND_SEGMENT));
    }

    private List<String> lines(GCLogFile logFile) throws IOException {
        try (Stream<String> lines = logFile.stream()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path zip(String name, Segment... segments) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream archiveStream = new ZipOutputStream(bytes)) {
            for (Segment segment : segments) {
                archiveStream.putNextEntry(new ZipEntry(segment.name));
                archiveStream.write(segment.contents.getBytes(StandardCharsets.UTF_8));
                archiveStream.closeEntry();
            }
        }
        return archive;
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream archiveStream = new GZIPOutputStream(bytes)) {
            archiveStream.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    private static Segment entry(String name, String contents) {
        return new Segment(name, contents);
    }

    private static final class Segment {
        private final String name;
        private final String contents;

        private Segment(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    /**
     * The number of file descriptors this JVM holds on {@code archive}.
     */
    private long openDescriptorsFor(Path archive) throws IOException {
        String expected = archive.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (expected.equals(Files.readSymbolicLink(descriptor).toString()))
                        count++;
                } catch (IOException expectedWhenTheDescriptorIsClosedDuringTheWalk) {
                    // A descriptor can disappear while /proc is being read.
                }
            }
        }
        return count;
    }
}

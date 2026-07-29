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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every archive opened to produce a {@code Stream<String>} has to be released when the caller
 * closes that stream, whether the stream was drained or abandoned part way through. The check is
 * made against {@code /proc/self/fd}, so it only runs where that is available.
 */
@EnabledOnOs(OS.LINUX)
class ZipArchiveDescriptorReleaseTest {

    private static final String FIRST_LINE = "[0.100s][info][gc] first";
    private static final String SECOND_LINE = "[0.200s][info][gc] second";
    private static final String CONTENTS = FIRST_LINE + "\n" + SECOND_LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void singleZipLogReleasesTheArchiveWhenTheDrainedStreamIsClosed() throws IOException {
        Path archive = zip("single.zip", "gc.log");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(Arrays.asList(FIRST_LINE, SECOND_LINE, GCLogFile.END_OF_DATA_SENTINEL), collect(lines));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipLogReleasesTheArchiveWhenAPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = zip("single.zip", "gc.log");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(FIRST_LINE, lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive has to be open while it is being read");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipLogReleasesTheArchiveWhenAPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = directory.resolve("single.log.gz");
        try (OutputStream bytes = Files.newOutputStream(archive); OutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(CONTENTS.getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(FIRST_LINE, lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive has to be open while it is being read");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesTheArchiveWhenAPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = zip("segment.zip", "gc.log");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(FIRST_LINE, lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive has to be open while it is being read");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesTheArchiveWhenTheStreamIsDrained() throws IOException {
        Path archive = zip("segment.zip", "gc.log");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(Arrays.asList(FIRST_LINE, SECOND_LINE), collect(lines));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesTheArchiveWhenTheSegmentIsMissing() throws IOException {
        Path archive = zip("segment.zip", "gc.log");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "absent.log").stream()) {
            assertEquals(0, collect(lines).size());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesTheArchiveWhenTheSegmentIsTimed() throws IOException {
        Path archive = zip("segment.zip", "gc.log");
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        assertEquals(0.100d, segment.getStartTime(), 0.001d);
        assertEquals(0.200d, segment.getEndTime(), 0.001d);

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipLogReleasesEveryArchiveWhenTheDrainedStreamIsClosed() throws IOException {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(rotatingLinesInOrder(), collect(lines));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipLogReleasesEveryArchiveWhenAPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.100s][info][gc] oldest", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipLogReleasesEveryArchiveWhileOrderingItsSegments() throws IOException {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        List<String> ordered = new ArrayList<>();
        new RotatingGCLogFile(archive).getMetaData().logFiles().map(LogFileSegment::getSegmentName).forEach(ordered::add);

        assertEquals(Arrays.asList("gc.log.0", "gc.log.1", "gc.log"), ordered);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void aRealRotatingArchiveIsReleasedWhetherItsStreamIsDrainedOrAbandoned() throws IOException {
        Path archive = new TestLogFile("rotating.zip").getFile().toPath();
        long baseline = descriptorsFor(archive);

        long lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.count();
        }
        assertTrue(lines > 1, "the archive has to contribute more than the sentinel");
        assertEquals(baseline, descriptorsFor(archive), "a drained stream left the archive open");

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertTrue(stream.iterator().hasNext(), "the archive has to be readable");
        }
        assertEquals(baseline, descriptorsFor(archive), "an abandoned stream left the archive open");
    }

    private List<String> collect(Stream<String> lines) {
        List<String> collected = new ArrayList<>();
        lines.forEach(collected::add);
        return collected;
    }

    private List<String> rotatingLinesInOrder() {
        return Arrays.asList(
                "[0.100s][info][gc] oldest", "[1.000s][info][gc] oldest ends",
                "[2.000s][info][gc] middle", "[3.000s][info][gc] middle ends",
                "[4.000s][info][gc] current", "[5.000s][info][gc] current ends",
                GCLogFile.END_OF_DATA_SENTINEL);
    }

    /**
     * An archive of rotating segments, deliberately stored out of order, so that the segment
     * ordering in the stream comes from the timestamps rather than from the archive layout.
     */
    private Path rotatingZip() throws IOException {
        Path archive = directory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            write(zip, "gc.log.1", "[2.000s][info][gc] middle\n[3.000s][info][gc] middle ends\n");
            write(zip, "gc.log", "[4.000s][info][gc] current\n[5.000s][info][gc] current ends\n");
            write(zip, "gc.log.0", "[0.100s][info][gc] oldest\n[1.000s][info][gc] oldest ends\n");
        }
        return archive;
    }

    private Path zip(String name, String entryName) throws IOException {
        Path archive = directory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            write(zip, entryName, CONTENTS);
        }
        return archive;
    }

    private void write(ZipOutputStream zip, String entryName, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /**
     * The number of descriptors this JVM currently holds on {@code file}.
     */
    private long descriptorsFor(Path file) throws IOException {
        String expected = file.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (expected.equals(Files.readSymbolicLink(descriptor).toString()))
                        count++;
                } catch (IOException ignored) {
                    // A descriptor can be closed while /proc/self/fd is being walked.
                }
            }
        }
        return count;
    }
}

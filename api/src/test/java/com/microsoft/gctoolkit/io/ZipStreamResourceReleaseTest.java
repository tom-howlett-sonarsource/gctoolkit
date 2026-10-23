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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamResourceReleaseTest {

    private static final String END_OF_DATA = GCLogFile.END_OF_DATA_SENTINEL;

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipFullyConsumedPreservesContentAndReleasesArchive() throws Exception {
        Path archive = zip("single.zip", new String[][]{{"gc.log", "[0.001s] first\n\n  second  \n"}});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("[0.001s] first", "second", END_OF_DATA), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesFileAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("single.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write("[0.001s] first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s] first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentMissingEntryReturnsEmptyStreamAndReleasesArchive() throws Exception {
        Path archive = zip("segment.zip", new String[][]{{"segment.log", "line\n"}});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimeQueriesReleaseArchive() throws Exception {
        Path archive = zip("segment.zip", new String[][]{{"segment.log", "1.000: a\n2.000: b\n"}});
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "segment.log");
        assertEquals(1.0d, segment.getStartTime());
        assertEquals(2.0d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesAllSegmentsAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("1.000: oldest", iterator.next());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of("1.000: oldest", "2.000: oldest", "3.000: middle", "4.000: middle",
                            "5.000: newest", "6.000: newest", END_OF_DATA),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip", new String[][]{
                {"gc.log", "5.000: newest\n6.000: newest\n"},
                {"gc.log.0", "1.000: oldest\n2.000: oldest\n"},
                {"gc.log.1", "3.000: middle\n4.000: middle\n"}});
    }

    private Path zip(String name, String[][] entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String[] entry : entries) {
                output.putNextEntry(new ZipEntry(entry[0]));
                output.write(entry[1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        String expected = archive.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).toString().replace(" (deleted)", "").equals(expected))
                        count++;
                } catch (IOException ignored) {
                    // descriptor closed during traversal
                }
            }
        }
        return count;
    }
}

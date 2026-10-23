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

    private static final String SEGMENT_0 = "[2021-01-01T00:00:01.000+0000][1.000s][info][gc] zero-a\n[2021-01-01T00:00:02.000+0000][2.000s][info][gc] zero-b\n";
    private static final String SEGMENT_1 = "[2021-01-01T00:00:03.000+0000][3.000s][info][gc] one-a\n[2021-01-01T00:00:04.000+0000][4.000s][info][gc] one-b\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesContentAndSentinelAndReleasesArchive() throws Exception {
        Path archive = zip("single.zip", "gc.log", "  first  \n\nsecond\n");
        long baseline = descriptorsFor(archive);
        SingleGCLogFile log = new SingleGCLogFile(archive);
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of("first", "second", log.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipReleasesFileAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("single.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeakArchive() throws Exception {
        Path archive = zip("segment.zip", "gc.log", SEGMENT_0);
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0d, segment.getStartTime());
        assertEquals(2.0d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithMissingEntryReturnsEmptyStreamWithoutLeaking() throws Exception {
        Path archive = zip("segment.zip", "gc.log", SEGMENT_0);
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderAndSentinelAndReleasesArchive() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        try (Stream<String> lines = log.stream()) {
            List<String> all = lines.collect(Collectors.toList());
            assertEquals(5, all.size());
            assertTrue(all.get(0).endsWith("zero-a"));
            assertTrue(all.get(3).endsWith("one-b"));
            assertEquals(log.endOfData(), all.get(4));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertTrue(iterator.next().endsWith("zero-a"));
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, "gc.log.0", SEGMENT_0);
            write(output, "gc.log", SEGMENT_1);
        }
        return archive;
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            write(output, entryName, contents);
        }
        return archive;
    }

    private static void write(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static long descriptorsFor(Path archive) throws IOException {
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

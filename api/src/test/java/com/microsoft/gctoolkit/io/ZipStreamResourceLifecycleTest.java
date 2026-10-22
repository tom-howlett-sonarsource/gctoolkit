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

class ZipStreamResourceLifecycleTest {
    @TempDir
    Path dir;

    @Test
    void singleZipFullyConsumedPreservesLinesAndEndOfData() throws Exception {
        Path archive = zip("single.zip", "gc.log", "[0.001s] first\n\n  second  \n");
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("[0.001s] first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipClosesFileAfterPartialConsumption() throws Exception {
        Path archive = dir.resolve("single.gz");
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
    void zipSegmentWithMissingEntryReturnsEmptyAndReleasesArchive() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "line\n");
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeakArchive() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "1.000: a\n2.000: b\n");
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "segment.log");
        segment.getStartTime();
        segment.getEndTime();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderAndClosesAfterPartialConsumption() throws Exception {
        Path archive = dir.resolve("rotating.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            entry(out, "gc.log.0", "2020-01-01T00:00:00.000+0000: 1.000: first\n2020-01-01T00:00:01.000+0000: 2.000: second\n");
            entry(out, "gc.log", "2020-01-01T00:00:02.000+0000: 3.000: third\n2020-01-01T00:00:03.000+0000: 4.000: fourth\n");
        }
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile log = new RotatingGCLogFile(archive);

        try (Stream<String> lines = log.stream()) {
            List<String> all = lines.collect(Collectors.toList());
            assertEquals(5, all.size());
            assertTrue(all.get(0).endsWith("first"));
            assertTrue(all.get(1).endsWith("second"));
            assertTrue(all.get(2).endsWith("third"));
            assertTrue(all.get(3).endsWith("fourth"));
            assertEquals(GCLogFile.END_OF_DATA_SENTINEL, all.get(4));
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = log.stream()) {
            Iterator<String> it = lines.iterator();
            assertTrue(it.next().endsWith("first"));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = dir.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(archive))) {
            entry(out, entryName, contents);
        }
        return archive;
    }

    private static void entry(ZipOutputStream out, String name, String contents) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(contents.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private static long descriptorsFor(Path archive) throws IOException {
        String expected = archive.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> fds = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path fd : fds) {
                try {
                    if (Files.readSymbolicLink(fd).toString().replace(" (deleted)", "").equals(expected))
                        count++;
                } catch (IOException ignored) {
                    // descriptor closed during traversal
                }
            }
        }
        return count;
    }
}

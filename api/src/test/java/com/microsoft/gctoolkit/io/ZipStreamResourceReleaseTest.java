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
    // Each segment spans distinct start and end times so rotating ordering can terminate.
    private static final String ZERO_A = "[2021-01-01T00:00:01.000+0000][1.000s][info][gc] zero a";
    private static final String ZERO_B = "[2021-01-01T00:00:02.000+0000][2.000s][info][gc] zero b";
    private static final String ONE_A = "[2021-01-01T00:00:03.000+0000][3.000s][info][gc] one a";
    private static final String ONE_B = "[2021-01-01T00:00:04.000+0000][4.000s][info][gc] one b";
    private static final String SEGMENT_0 = ZERO_A + "\n" + ZERO_B + "\n";
    private static final String SEGMENT_1 = ONE_A + "\n" + ONE_B + "\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipFullyConsumedPreservesLinesAndSentinelAndCloses() throws Exception {
        Path archive = zip("single.zip", new String[]{"gc.log"}, new String[]{"  first  \n\nsecond\n"});
        long baseline = descriptorsFor(archive);
        SingleGCLogFile log = new SingleGCLogFile(archive);
        List<String> lines;
        try (Stream<String> stream = log.stream()) {
            lines = stream.collect(Collectors.toList());
        }
        assertEquals(List.of("first", "second", log.endOfData()), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipPartiallyConsumedClosesFile() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentMissingEntryReturnsEmptyStreamWithoutLeak() throws Exception {
        Path archive = zip("segment.zip", new String[]{"a.log"}, new String[]{SEGMENT_0});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeak() throws Exception {
        Path archive = zip("segment.zip", new String[]{"gc.log"}, new String[]{SEGMENT_0});
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0d, segment.getStartTime());
        assertEquals(2.0d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPartiallyConsumedClosesArchive() throws Exception {
        Path archive = zip("rotating.zip", new String[]{"gc.log.0", "gc.log"}, new String[]{SEGMENT_0, SEGMENT_1});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals(ZERO_A, iterator.next());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderAndSentinelAndCloses() throws Exception {
        Path archive = zip("rotating.zip", new String[]{"gc.log", "gc.log.0"}, new String[]{SEGMENT_1, SEGMENT_0});
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        List<String> lines;
        try (Stream<String> stream = log.stream()) {
            lines = stream.collect(Collectors.toList());
        }
        assertEquals(List.of(ZERO_A, ZERO_B, ONE_A, ONE_B, log.endOfData()), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String[] entryNames, String[] contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < entryNames.length; i++) {
                output.putNextEntry(new ZipEntry(entryNames[i]));
                output.write(contents[i].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
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

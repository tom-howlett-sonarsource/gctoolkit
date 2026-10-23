// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamResourceReleaseTest {

    private static final String SEGMENT_0_FIRST = "[0.001s][info][gc] segment0";
    private static final String SEGMENT_0_LAST = "[0.500s][info][gc] segment0 end";
    private static final String SEGMENT_1_FIRST = "[1.001s][info][gc] segment1";
    private static final String SEGMENT_1_LAST = "[1.500s][info][gc] segment1 end";
    private static final String SEGMENT_0 = SEGMENT_0_FIRST + "\n" + SEGMENT_0_LAST + "\n";
    private static final String SEGMENT_1 = SEGMENT_1_FIRST + "\n" + SEGMENT_1_LAST + "\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesContentAndSentinelAndClosesArchive() throws Exception {
        Path archive = zip("single.zip", new String[]{"gc.log"}, new String[]{"  a  \n\nb\n"});
        long baseline = descriptorsFor(archive);
        SingleGCLogFile log = new SingleGCLogFile(archive);
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of("a", "b", log.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipClosesFileAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write(SEGMENT_0.concat(SEGMENT_1).getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(SEGMENT_0_FIRST, lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeakArchive() throws Exception {
        Path archive = zip("segment.zip", new String[]{"gc.log"}, new String[]{SEGMENT_0});
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(0.001d, segment.getStartTime(), 1e-9);
        assertEquals(0.5d, segment.getEndTime(), 1e-9);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithMissingEntryIsEmptyAndClosesArchive() throws Exception {
        Path archive = zip("segment.zip", new String[]{"gc.log"}, new String[]{SEGMENT_0});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderAndSentinelAndClosesArchive() throws Exception {
        Path archive = zip("rotating.zip", new String[]{"gc.log", "gc.log.0"}, new String[]{SEGMENT_1, SEGMENT_0});
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(SEGMENT_0_FIRST, SEGMENT_0_LAST, SEGMENT_1_FIRST, SEGMENT_1_LAST, log.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip", new String[]{"gc.log", "gc.log.0"}, new String[]{SEGMENT_1, SEGMENT_0});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(SEGMENT_0_FIRST, lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void closeAllClosesEveryResourceAndReportsFirstFailure() {
        List<String> closed = new ArrayList<>();
        Closeable failing = () -> { closed.add("first"); throw new IOException("first"); };
        Closeable second = () -> closed.add("second");
        Closeable alsoFailing = () -> { closed.add("third"); throw new IOException("third"); };
        IOException thrown = assertThrows(IOException.class, () -> LineStreams.closeAll(failing, second, alsoFailing));
        assertEquals("first", thrown.getMessage());
        assertEquals("third", thrown.getSuppressed()[0].getMessage());
        assertEquals(List.of("first", "second", "third"), closed);
    }

    private Path zip(String name, String[] entries, String[] contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int i = 0; i < entries.length; i++) {
                output.putNextEntry(new ZipEntry(entries[i]));
                output.write(contents[i].getBytes(StandardCharsets.UTF_8));
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

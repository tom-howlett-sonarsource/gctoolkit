// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamResourceLifecycleTest {

    private static final String SEGMENT_0 = "[2021-01-01T00:00:00.000+0000][0.001s][info][gc] zero-a\n[2021-01-01T00:00:01.000+0000][1.000s][info][gc] zero-b\n";
    private static final String SEGMENT_1 = "[2021-01-01T00:00:02.000+0000][2.000s][info][gc] one-a\n[2021-01-01T00:00:03.000+0000][3.000s][info][gc] one-b\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesAllSegmentsAfterPartialIteration() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        logFile.getMetaData().logFiles().collect(Collectors.toList()); // warm metadata
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = logFile.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertTrue(iterator.next().endsWith("zero-a"));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesAfterShortCircuit() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        logFile.getMetaData();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertTrue(lines.filter(l -> l.endsWith("one-a")).findFirst().isPresent());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderAndSentinel() throws Exception {
        Path archive = rotatingZip();
        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }
        assertEquals(5, lines.size());
        assertTrue(lines.get(0).endsWith("zero-a"));
        assertTrue(lines.get(1).endsWith("zero-b"));
        assertTrue(lines.get(2).endsWith("one-a"));
        assertTrue(lines.get(3).endsWith("one-b"));
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, lines.get(4));
    }

    @Test
    void singleZipPreservesContentsAndSentinel() throws Exception {
        Path archive = zip("single.zip", List.of("gc.log"), List.of("  first  \n\nsecond\n"));
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), stream.collect(Collectors.toList()));
        }
        assertEquals(0, descriptorsFor(archive));
    }

    @Test
    void singleGzipClosesAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write("[0.001s][info][gc] first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > 0);
        }
        assertEquals(0, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeak() throws Exception {
        Path archive = zip("segment.zip", List.of("gc.log"), List.of(SEGMENT_0));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(0.001d, segment.getStartTime(), 1e-9);
        assertEquals(1.0d, segment.getEndTime(), 1e-9);
        assertEquals(0, descriptorsFor(archive));
    }

    @Test
    void flatMapClosingClosesEveryOpenedInnerStream() {
        AtomicInteger closed = new AtomicInteger();
        try (Stream<Integer> stream = StreamResources.flatMapClosing(Stream.of(1, 2, 3),
                i -> Stream.of(i, i).onClose(closed::incrementAndGet))) {
            Iterator<Integer> iterator = stream.iterator();
            iterator.next();
            iterator.next();
            iterator.next();
        }
        assertEquals(2, closed.get());
    }

    @Test
    void linesClosesBothResourcesAndReportsFailures() {
        AtomicInteger closed = new AtomicInteger();
        BufferedReader reader = new BufferedReader(new StringReader("a"));
        Stream<String> lines = StreamResources.lines(reader, () -> {
            closed.incrementAndGet();
            throw new IOException("boom");
        });
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertEquals("boom", failure.getCause().getMessage());
        assertEquals(1, closed.get());
        assertThrows(IOException.class, reader::read);
    }

    private Path rotatingZip() throws IOException {
        return zip("gc.log.zip", List.of("gc.log", "gc.log.0"), List.of(SEGMENT_1, SEGMENT_0));
    }

    private Path zip(String name, List<String> entries, List<String> contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (int i = 0; i < entries.size(); i++) {
                output.putNextEntry(new ZipEntry(entries.get(i)));
                output.write(contents.get(i).getBytes(StandardCharsets.UTF_8));
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

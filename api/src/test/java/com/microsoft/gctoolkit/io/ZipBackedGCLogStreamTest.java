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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers line-content preservation, rotating-segment ordering, the end-of-data sentinel,
 * and archive resource cleanup for ZIP-backed GC log streams, in addition to what is
 * already covered by {@link VisibleZipStreamResourceLifecycleTest}.
 */
class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFilePreservesLinesAndAppendsSentinel() throws Exception {
        Path archive = zip("single.zip", "gc.log",
                "[0.001s][info][gc] first\n" +
                "  \n" +
                "[0.002s][info][gc] second  \n" +
                "\n" +
                "[0.003s][info][gc] third\n");

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] first",
                "[0.002s][info][gc] second",
                "[0.003s][info][gc] third",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
    }

    @Test
    void zipSegmentPreservesLineContentVerbatim() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "first\nsecond\nthird\n");

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first", "second", "third"), lines);
    }

    @Test
    void rotatingZipOrdersSegmentsOldestFirstAndAppendsSentinel() throws Exception {
        Path archive = rotatingZip();

        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        List<String> segmentOrder = logFile.getMetaData().logFiles()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log.0", "gc.log.1.current"), segmentOrder);

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "2016-08-30T13:35:57.614+0200: 1.000: [GC pause (young)]",
                "2016-08-30T13:35:58.614+0200: 2.000: [GC pause (young)]",
                "2016-08-30 20:35:29 GC log file has reached the maximum size. Saved as /tmp/gc.log.0",
                "2016-08-30 20:35:29 GC log file created /tmp/gc.log.1",
                "2016-08-30T20:35:49.714+0200: 10.000: [GC pause (young)]",
                "2016-08-30T20:35:50.714+0200: 20.000: [GC pause (young)]",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
    }

    @Test
    void rotatingZipReleasesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        try (Stream<String> stream = logFile.stream()) {
            long count = stream.count();
            assertTrue(count > 0);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipReleasesArchiveWhenClosedAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        try (Stream<String> stream = logFile.stream()) {
            Iterator<String> lines = stream.iterator();
            assertTrue(lines.hasNext());
            lines.next();
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write((
                    "2016-08-30T13:35:57.614+0200: 1.000: [GC pause (young)]\n" +
                    "2016-08-30T13:35:58.614+0200: 2.000: [GC pause (young)]\n" +
                    "2016-08-30 20:35:29 GC log file has reached the maximum size. Saved as /tmp/gc.log.0\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();

            output.putNextEntry(new ZipEntry("gc.log.1.current"));
            output.write((
                    "2016-08-30 20:35:29 GC log file created /tmp/gc.log.1\n" +
                    "2016-08-30T20:35:49.714+0200: 10.000: [GC pause (young)]\n" +
                    "2016-08-30T20:35:50.714+0200: 20.000: [GC pause (young)]\n"
            ).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) {
                        count++;
                    }
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage for resource lifecycle beyond {@link VisibleZipStreamResourceLifecycleTest}:
 * GZip streams, the segment-metadata helpers that stream a {@link GCLogFileZipSegment} internally,
 * and the composed stream produced by {@link RotatingGCLogFile} for a rotating zip archive.
 */
class ZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipStreamPreservesLineContentsAndSentinel() throws Exception {
        Path archive = zip("contents.zip", "gc.log", "[0.001s][info][gc] first\nsecond\nthird\n");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of("[0.001s][info][gc] first", "second", "third", GCLogFile.END_OF_DATA_SENTINEL), collected);
        }
    }

    @Test
    void zipSegmentMetadataHelpersDoNotLeakArchiveHandles() throws Exception {
        Path archive = zip("metadata.zip", "segment.log",
                "2016-08-30 20:30:00 GC log file created /var/log/x/segment.log\n" +
                "2016-08-30T20:30:01.000+0000: 0.500: [GC pause] first\n" +
                "2016-08-30T20:30:02.000+0000: 1.500: [GC pause] second\n");
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "segment.log");
        assertEquals(0.5d, segment.getStartTime());
        assertEquals(1.5d, segment.getEndTime());

        assertEquals(baseline, descriptorsFor(archive), "computing start/end time must not leak the archive handle");
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderingAndSentinel() throws Exception {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(
                    "2016-08-30 20:00:00 GC log file created /var/log/x/gc.log.0",
                    "2016-08-30T20:00:01.000+0000: 0.500: [GC pause] older segment line one",
                    "2016-08-30T20:00:02.000+0000: 1.500: [GC pause] older segment line two",
                    "2016-08-30 20:00:03 GC log file has reached the maximum size. Saved as /var/log/x/gc.log.0",
                    "2016-08-30 20:00:03 GC log file created /var/log/x/gc.log.1",
                    "2016-08-30T20:00:04.000+0000: 100.500: [GC pause] current segment line one",
                    "2016-08-30T20:00:05.000+0000: 101.500: [GC pause] current segment line two",
                    GCLogFile.END_OF_DATA_SENTINEL
            ), collected);
        }
    }

    @Test
    void rotatingZipStreamClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("2016-08-30 20:00:00 GC log file created /var/log/x/gc.log.0", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0",
                    "2016-08-30 20:00:00 GC log file created /var/log/x/gc.log.0\n" +
                    "2016-08-30T20:00:01.000+0000: 0.500: [GC pause] older segment line one\n" +
                    "2016-08-30T20:00:02.000+0000: 1.500: [GC pause] older segment line two\n" +
                    "2016-08-30 20:00:03 GC log file has reached the maximum size. Saved as /var/log/x/gc.log.0\n");
            writeEntry(output, "gc.log.1.current",
                    "2016-08-30 20:00:03 GC log file created /var/log/x/gc.log.1\n" +
                    "2016-08-30T20:00:04.000+0000: 100.500: [GC pause] current segment line one\n" +
                    "2016-08-30T20:00:05.000+0000: 101.500: [GC pause] current segment line two\n");
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, entryName, contents);
        }
        return archive;
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             java.util.zip.GZIPOutputStream output = new java.util.zip.GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
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

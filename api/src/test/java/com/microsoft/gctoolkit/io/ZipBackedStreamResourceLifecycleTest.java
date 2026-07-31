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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional resource-lifecycle coverage for ZIP/GZip-backed GC log streams, complementing
 * {@link VisibleZipStreamResourceLifecycleTest}. These tests cover full consumption (not just
 * partial), GZip-backed {@link SingleGCLogFile}s, and the composed stream returned by
 * {@link RotatingGCLogFile} for a zip-backed rotating log, including partial consumption of
 * that composed stream.
 */
class ZipBackedStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip("single-full.zip", "gc.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = gzip("single.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = gzip("single-full.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = zip("segment-full.zip", "segment.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderContentsAndSentinelAndClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip("rotating-full.zip");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] segment0-line1",
                "[0.002s][info][gc] segment0-line2",
                "[10.001s][info][gc] segment1-line1",
                "[10.002s][info][gc] segment1-line2",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] segment0-line1", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
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

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    // Segments must be ordered by embedded time stamps rather than file name: gc.log.0 ends
    // before gc.log.1.current starts, so RotatingLogFileMetadata orders gc.log.0 first.
    private Path rotatingZip(String name) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write("[0.001s][info][gc] segment0-line1\n[0.002s][info][gc] segment0-line2\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.1.current"));
            output.write("[10.001s][info][gc] segment1-line1\n[10.002s][info][gc] segment1-line2\n".getBytes(StandardCharsets.UTF_8));
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

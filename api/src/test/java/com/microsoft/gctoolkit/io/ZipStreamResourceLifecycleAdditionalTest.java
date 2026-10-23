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
 * Additional coverage for releasing archive resources held by ZIP-backed GC log streams, complementing
 * {@code VisibleZipStreamResourceLifecycleTest}. These tests exercise the composed stream produced by
 * {@link RotatingGCLogFile}, verify full-consumption close, and assert that line contents, rotating-segment
 * ordering, and the end-of-data sentinel are preserved.
 */
class ZipStreamResourceLifecycleAdditionalTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = singleEntryZip("full.zip", "gc.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            lines.forEach(line -> {});
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipPreservesContentsAndSentinel() throws Exception {
        Path archive = singleEntryZip("content.zip", "gc.log", "[0.001s][info][gc] first\n\n  second  \nthird\n");

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(
                List.of("[0.001s][info][gc] first", "second", "third", GCLogFile.END_OF_DATA_SENTINEL),
                lines);
    }

    @Test
    void zipSegmentClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = singleEntryZip("seg-full.zip", "segment.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines.forEach(line -> {});
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("2020-01-01T00:00:00.000-0000: 0.001: first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            lines.forEach(line -> {});
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderingContentsAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // Oldest segment first, current (highest timestamps) last; the sentinel terminates the data.
        assertEquals(
                List.of(
                        "2020-01-01T00:00:00.000-0000: 0.001: first",
                        "2020-01-01T00:00:01.000-0000: 1.000: second",
                        "2020-01-01T00:00:02.000-0000: 2.000: third",
                        "2020-01-01T00:00:03.000-0000: 3.000: fourth",
                        GCLogFile.END_OF_DATA_SENTINEL),
                lines);
    }

    /**
     * Build a two-segment rotating log inside a single ZIP archive. The entry {@code rotating.log.0} holds
     * the older events and {@code rotating.log} (no index) is the current segment with the later timestamps.
     */
    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.log.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            write(output, "rotating.log.0",
                    "2020-01-01T00:00:00.000-0000: 0.001: first\n2020-01-01T00:00:01.000-0000: 1.000: second\n");
            write(output, "rotating.log",
                    "2020-01-01T00:00:02.000-0000: 2.000: third\n2020-01-01T00:00:03.000-0000: 3.000: fourth\n");
        }
        return archive;
    }

    private void write(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private Path singleEntryZip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            write(output, entryName, contents);
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

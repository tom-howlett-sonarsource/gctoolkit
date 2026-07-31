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
 * Companion to {@link VisibleZipStreamResourceLifecycleTest}: covers the GZip-backed
 * {@link SingleGCLogFile} path, line content/ordering preservation, and the composed
 * stream returned by {@link RotatingGCLogFile} when it is backed by a rotating zip archive.
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
    void singleZipPreservesLineContentOrderAndSentinel() throws Exception {
        Path archive = zip("ordered.zip", "gc.log", "first line\n\nsecond line\n   third line   \n");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            List<String> actual = lines.collect(Collectors.toList());
            assertEquals(List.of("first line", "second line", "third line", GCLogFile.END_OF_DATA_SENTINEL), actual);
        }
    }

    @Test
    void zipSegmentPreservesRawLineContentAndOrder() throws Exception {
        Path archive = zip("segment-content.zip", "segment.log", "alpha\nbeta\ngamma\n");

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            List<String> actual = lines.collect(Collectors.toList());
            assertEquals(List.of("alpha", "beta", "gamma"), actual);
        }
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = new TestLogFile("rotating.zip").getFile().toPath();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertTrue(lines.findFirst().isPresent());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipOrdersSegmentsAndPreservesContent() throws Exception {
        Path archive = new TestLogFile("rotating.zip").getFile().toPath();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            List<String> all = lines.collect(Collectors.toList());
            // gc.log.0 (the older, rotated-out segment) is streamed before gc.log.1.current.
            assertEquals(72210, all.size());
            assertTrue(all.get(0).startsWith("Java HotSpot(TM)"), "expected the older segment's first line");
            assertEquals(GCLogFile.END_OF_DATA_SENTINEL, all.get(all.size() - 1));
        }
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

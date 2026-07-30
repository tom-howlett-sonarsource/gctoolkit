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

/**
 * Additional coverage for the ZIP-backed resource lifecycle fix, complementing
 * {@link VisibleZipStreamResourceLifecycleTest}: full consumption release, and the
 * composed stream returned by {@link RotatingGCLogFile} when it is backed by a ZIP archive.
 */
class ZipStreamResourceLifecycleTest {

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
    void rotatingZipComposedStreamPreservesOrderingContentAndSentinel() throws Exception {
        Path archive = rotatingZip("rotating.zip");

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);
        List<String> orderedSegmentNames = rotatingGCLogFile.getOrderedGarbageCollectionLogFiles()
                .stream()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log.0", "gc.log"), orderedSegmentNames);

        List<String> lines;
        try (Stream<String> stream = rotatingGCLogFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(
                List.of(
                        "[0.001s][info][gc] first",
                        "[0.002s][info][gc] second",
                        "[5.001s][info][gc] third",
                        "[5.002s][info][gc] fourth",
                        GCLogFile.END_OF_DATA_SENTINEL),
                lines);
    }

    @Test
    void rotatingZipComposedStreamClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);
        try (Stream<String> stream = rotatingGCLogFile.stream()) {
            assertEquals("[0.001s][info][gc] first", stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip(String name) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
            writeEntry(output, "gc.log", "[5.001s][info][gc] third\n[5.002s][info][gc] fourth\n");
        }
        return archive;
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, entryName, contents);
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
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

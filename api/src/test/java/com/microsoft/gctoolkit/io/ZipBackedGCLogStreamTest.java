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
 * Covers resource release, line content, ordering, and the end-of-data sentinel for
 * ZIP/GZIP-backed {@link GCLogFile} streams. Complements
 * {@link VisibleZipStreamResourceLifecycleTest}, which only checks that
 * {@link SingleGCLogFile} and {@link GCLogFileZipSegment} release the archive handle
 * on close.
 */
class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamPreservesContentOrderAndSentinel() throws Exception {
        Path archive = zip("full.zip", "gc.log",
                "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] first",
                "[0.002s][info][gc] second",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipStreamClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesLineContent() throws Exception {
        Path archive = zip("segment.zip", "segment.log",
                "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second"), lines);
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = zip("rotating.zip",
                "app.log", "[10.000s][info][gc] current-first\n[10.001s][info][gc] current-second\n",
                "app.log.0", "[0.000s][info][gc] older-first\n[0.001s][info][gc] older-second\n");

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);

        List<String> expectedLines;
        try (Stream<String> raw = rotatingGCLogFile.getOrderedGarbageCollectionLogFiles()
                .stream()
                .flatMap(LogFileSegment::stream)) {
            expectedLines = raw.map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toCollection(java.util.ArrayList::new));
        }
        expectedLines.add(GCLogFile.END_OF_DATA_SENTINEL);

        List<String> actualLines;
        try (Stream<String> stream = rotatingGCLogFile.stream()) {
            actualLines = stream.collect(Collectors.toList());
        }

        assertEquals(expectedLines, actualLines);
        assertEquals(4, actualLines.size() - 1, "both segments must contribute their lines");
    }

    @Test
    void rotatingZipStreamReleasesArchivesAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating-partial.zip",
                "app.log", "[10.000s][info][gc] current-first\n[10.001s][info][gc] current-second\n",
                "app.log.0", "[0.000s][info][gc] older-first\n[0.001s][info][gc] older-second\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertTrue(stream.findFirst().isPresent());
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

    private Path zip(String name, String firstEntryName, String firstContents, String secondEntryName, String secondContents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(firstEntryName));
            output.write(firstContents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry(secondEntryName));
            output.write(secondContents.getBytes(StandardCharsets.UTF_8));
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

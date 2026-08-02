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

class ZipBackedStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogCloseReleasesZipAfterPartialConsumption() throws Exception {
        Path archive = zip("single.zip", List.of(
                entry("gc.log", " first \nsecond\n")));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentCloseReleasesZipAfterPartialConsumption() throws Exception {
        Path archive = zip("segment.zip", List.of(
                entry("gc.log", "first\nsecond\n")));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogCloseReleasesCurrentZipSegmentAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip", List.of(
                entry("gc.log", "[2.000s][info][gc] current\n[2.500s][info][gc] current-end\n")));
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = log.stream()) {
            assertEquals("[2.000s][info][gc] current", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogPreservesContentsAndSentinel() throws Exception {
        Path archive = zip("ordered.zip", List.of(
                entry("gc.log", " [2.000s][info][gc] current \n[2.500s][info][gc] current-end\n")));
        RotatingGCLogFile log = new RotatingGCLogFile(archive);

        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(
                    "[2.000s][info][gc] current",
                    "[2.500s][info][gc] current-end",
                    log.endOfData()), lines.collect(Collectors.toList()));
        }
    }

    private Path zip(String name, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (ArchiveEntry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private ArchiveEntry entry(String name, String contents) {
        return new ArchiveEntry(name, contents);
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) count++;
                } catch (IOException ignored) {
                    // A descriptor may disappear while /proc is traversed.
                }
            }
        }
        return count;
    }

    private static class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

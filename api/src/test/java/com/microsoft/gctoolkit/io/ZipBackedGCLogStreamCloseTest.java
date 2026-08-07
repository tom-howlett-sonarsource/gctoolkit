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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipBackedGCLogStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipRetainsLinesAndSentinelWhileClosingArchive() throws Exception {
        Path archive = zip("single.zip", List.of(
                entry("gc.log", "  first  \n\nsecond\n")));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(java.util.stream.Collectors.toList());
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentRetainsRawLinesWhileClosingArchive() throws Exception {
        Path archive = zip("segment.zip", List.of(
                entry("segment.log", "  first  \nsecond\n")));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(java.util.stream.Collectors.toList());
        }

        assertEquals(List.of("  first  ", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[1.000s][info][gc] older", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipRetainsSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(java.util.stream.Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] older",
                "[1.500s][info][gc] older tail",
                "[2.000s][info][gc] current",
                "[2.500s][info][gc] current tail",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive() throws IOException {
        return zip("rotating.zip", List.of(
                entry("gc.log.0", "[1.000s][info][gc] older\n[1.500s][info][gc] older tail\n"),
                entry("gc.log", "[2.000s][info][gc] current\n[2.500s][info][gc] current tail\n")));
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

    private static class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

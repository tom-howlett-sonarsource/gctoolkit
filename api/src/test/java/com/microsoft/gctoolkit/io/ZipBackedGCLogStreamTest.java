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

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesLinesAndEndOfDataSentinel() throws Exception {
        Path archive = zip("single.zip", List.of(
                entry("gc.log", " first \n\nsecond\n")));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentTimeInspectionDoesNotLeakArchive() throws Exception {
        Path archive = zip("segment.zip", List.of(
                entry("gc.log", "[1.000s][info][gc] first\n[1.500s][info][gc] second\n")));
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        assertEquals(1.0d, segment.getStartTime());
        assertEquals(1.5d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertTrue(iterator.hasNext());
            assertEquals("[1.000s][info][gc] old-start", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderLinesAndEndOfDataSentinel() throws Exception {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[1.000s][info][gc] old-start",
                            "[1.500s][info][gc] old-end",
                            "[2.000s][info][gc] middle-start",
                            "[2.500s][info][gc] middle-end",
                            "[3.000s][info][gc] current-start",
                            "[3.500s][info][gc] current-end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip", List.of(
                entry("gc.log.1", "[1.000s][info][gc] old-start\n[1.500s][info][gc] old-end\n"),
                entry("gc.log.0", "[2.000s][info][gc] middle-start\n[2.500s][info][gc] middle-end\n"),
                entry("gc.log", "[3.000s][info][gc] current-start\n[3.500s][info][gc] current-end\n")));
    }

    private Path zip(String name, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
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

    private static final class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

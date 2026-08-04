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

class ZipBackedGCLogStreamCloseTest {

    private static final String OLD_SEGMENT =
            "[0.001s][info][gc] old first\n[0.002s][info][gc] old second\n";
    private static final String CURRENT_SEGMENT =
            "[1.001s][info][gc] current first\n[1.002s][info][gc] current second\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamRetainsContentsAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("single-full.zip", new ArchiveEntry("gc.log", " first \n\n second\n"));
        long baseline = descriptorsFor(archive);

        List<String> contents;
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            contents = lines.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until stream close");
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), contents);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentStreamClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("segment-partial.zip", new ArchiveEntry("gc.log", " first \nsecond\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(" first ", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open while the stream is open");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipStreamRetainsSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingArchive("rotating-full.zip");
        long baseline = descriptorsFor(archive);

        List<String> contents;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            contents = lines.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] old first",
                "[0.002s][info][gc] old second",
                "[1.001s][info][gc] current first",
                "[1.002s][info][gc] current second",
                GCLogFile.END_OF_DATA_SENTINEL), contents);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipStreamClosesAllArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive("rotating-partial.zip");
        long baseline = descriptorsFor(archive);
        Stream<String> lines = new RotatingGCLogFile(archive).stream();

        try (lines) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.001s][info][gc] old first", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive(String name) throws IOException {
        return zip(name,
                new ArchiveEntry("gc.log.0", OLD_SEGMENT),
                new ArchiveEntry("gc.log", CURRENT_SEGMENT));
    }

    private Path zip(String name, ArchiveEntry... entries) throws IOException {
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

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

class ZipStreamClosePropagationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleAndSegmentStreamsRetainContentsAndCloseTheirArchives() throws Exception {
        Path archive = zip("single.zip", List.of(
                new Entry("gc.log", " first \n\nsecond\n")));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(List.of(" first ", "", "second"), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void closingPartiallyConsumedRotatingStreamClosesTheCurrentArchiveEntry() throws Exception {
        Path archive = rotatingLargeZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.500s][info][gc] older start", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingStreamRetainsSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[0.500s][info][gc] older start",
                            "[1.000s][info][gc] older",
                            "[1.500s][info][gc] current start",
                            "[2.000s][info][gc] current",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip", List.of(
                new Entry("gc.log.0", "[0.500s][info][gc] older start\n[1.000s][info][gc] older\n"),
                new Entry("gc.log", "[1.500s][info][gc] current start\n[2.000s][info][gc] current\n")));
    }

    private Path rotatingLargeZip() throws IOException {
        String older = "[0.500s][info][gc] older start\n" + "padding padding padding\n".repeat(10_000)
                + "[1.000s][info][gc] older\n";
        return zip("rotating-large.zip", List.of(
                new Entry("gc.log.0", older),
                new Entry("gc.log", "[1.500s][info][gc] current start\n[2.000s][info][gc] current\n")));
    }

    private Path zip(String name, List<Entry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Entry entry : entries) {
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

    private static final class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamClosePropagationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleStreamPreservesLinesAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until the stream is closed");
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentStreamPreservesLinesAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("segment.zip", Map.of("gc.log", "first\nsecond\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until the stream is closed");
        }

        assertEquals(List.of("first", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingStreamClosesArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[1.000s][info][gc] old first", stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingStreamPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingZip("rotating-complete.zip");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] old first",
                "[1.500s][info][gc] old last",
                "[2.000s][info][gc] current first",
                "[2.500s][info][gc] current last",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip(String name) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s][info][gc] old first\n[1.500s][info][gc] old last\n");
        entries.put("gc.log", "[2.000s][info][gc] current first\n[2.500s][info][gc] current last\n");
        return zip(name, entries);
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
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
}

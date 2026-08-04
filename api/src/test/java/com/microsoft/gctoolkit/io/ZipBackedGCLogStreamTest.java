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

class ZipBackedGCLogStreamTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipRetainsProcessedLinesAndSentinel() throws Exception {
        Path archive = zip("single-content.zip", Map.of(
                "gc.log", "  [0.001s][info][gc] first  \n\n second \n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[0.001s][info][gc] first",
                    "second",
                    GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentRetainsUnprocessedLineContents() throws Exception {
        Path archive = zip("segment-content.zip", Map.of(
                "segment.log", "  first  \n\nsecond\n"));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(List.of("  first  ", "", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipRetainsOrderAndSentinelAndClosesAfterPartialConsumption() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] old first\n[0.002s][info][gc] old second\n");
        entries.put("gc.log", "[0.003s][info][gc] current first\n[0.004s][info][gc] current second\n");
        Path archive = zip("rotating.zip", entries);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[0.001s][info][gc] old first",
                    "[0.002s][info][gc] old second",
                    "[0.003s][info][gc] current first",
                    "[0.004s][info][gc] current second",
                    GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] old first", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
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

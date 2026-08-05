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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipStreamClosureRegressionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesLinesAndSentinelAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("single-full.zip",
                entry("gc.log", "  first line  \n\nsecond line\n"));
        long baseline = descriptorsFor(archive);

        List<String> actual;
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), actual);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesUnprocessedLinesAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("segment-full.zip",
                entry("segment.log", "  first line  \n\nsecond line\n"));
        long baseline = descriptorsFor(archive);

        List<String> actual;
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList("  first line  ", "", "second line"), actual);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesAfterPartialConsumptionAndPreservesOrderAndSentinel() throws Exception {
        Path archive = zip("rotating.zip",
                entry("gc.log.0", "[0.100s][info][gc] old first\n[0.200s][info][gc] old second\n"),
                entry("gc.log", "[0.300s][info][gc] new first\n[0.400s][info][gc] new second\n"));
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("[0.100s][info][gc] old first", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));

        List<String> actual;
        try (Stream<String> lines = logFile.stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList(
                "[0.100s][info][gc] old first",
                "[0.200s][info][gc] old second",
                "[0.300s][info][gc] new first",
                "[0.400s][info][gc] new second",
                GCLogFile.END_OF_DATA_SENTINEL), actual);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private String[] entry(String name, String contents) {
        return new String[] {name, contents};
    }

    private Path zip(String name, String[]... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (String[] entry : entries) {
                output.putNextEntry(new ZipEntry(entry[0]));
                output.write(entry[1].getBytes(StandardCharsets.UTF_8));
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

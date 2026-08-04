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

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogPreservesLinesAndSentinelAndClosesArchive() throws Exception {
        Path archive = zip("single.zip", Map.of(
                "gc.log", "  [0.001s][info][gc] first  \n\n second \n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until the stream is closed");
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesLineContentsAndClosesArchive() throws Exception {
        Path archive = zip("segment.zip", Map.of(
                "segment.log", "  first  \n\nsecond\n"));
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until the stream is closed");
        }

        assertEquals(List.of("  first  ", "", "second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void partiallyConsumedRotatingLogClosesEverySegmentArchive() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s][info][gc] oldest\n[2.000s][info][gc] oldest end\n");
        entries.put("gc.log.1", "[3.000s][info][gc] middle\n[4.000s][info][gc] middle end\n");
        entries.put("gc.log", "[5.000s][info][gc] current\n[6.000s][info][gc] current end\n");
        Path archive = zip("rotating.zip", entries);
        long baseline = descriptorsFor(archive);

        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        try (Stream<String> stream = logFile.stream()) {
            assertEquals("[1.000s][info][gc] oldest", stream.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until the stream is closed");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogPreservesSegmentOrderLinesAndSentinel() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s][info][gc] oldest\n[1.500s][info][gc] oldest end\n");
        entries.put("gc.log.1", "[2.000s][info][gc] middle  \n\n[2.500s][info][gc] middle end\n");
        entries.put("gc.log", "[3.000s][info][gc] current\n[3.500s][info][gc] current end\n");
        Path archive = zip("ordered.zip", entries);
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] oldest",
                "[1.500s][info][gc] oldest end",
                "[2.000s][info][gc] middle",
                "[2.500s][info][gc] middle end",
                "[3.000s][info][gc] current",
                "[3.500s][info][gc] current end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
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

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogClosesZipAfterPartialConsumption() throws Exception {
        Path archive = zip("single.zip", Map.of(
                "gc.log", " [0.001s][info][gc] first \n\nsecond\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the ZIP should be open while its stream is active");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesLinesAndClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("segment.zip", Map.of(
                "gc.log", " first \n\nsecond\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of(" first ", "", "second"), lines.collect(Collectors.toList()));
        }

        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = segment.stream()) {
            assertEquals(" first ", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the ZIP should be open while its stream is active");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogPreservesSegmentOrderAndSentinel() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.1", "[1.000s][info][gc] old first\n[2.000s][info][gc] old second\n");
        entries.put("gc.log", "[3.000s][info][gc] current first\n[4.000s][info][gc] current second\n");
        Path archive = zip("rotating.zip", entries);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] old first",
                "[2.000s][info][gc] old second",
                "[3.000s][info][gc] current first",
                "[4.000s][info][gc] current second",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void rotatingLogClosesZipAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating-partial.zip", Map.of(
                "gc.log", "[1.000s][info][gc] first\n[2.000s][info][gc] second\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] first", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorDirectory), "file descriptor checks require procfs");
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(descriptorDirectory)) {
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

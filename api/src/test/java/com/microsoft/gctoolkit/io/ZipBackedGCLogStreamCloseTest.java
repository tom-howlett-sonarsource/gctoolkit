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

class ZipBackedGCLogStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleFileStreamPreservesLinesAndClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentStreamClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("segment.zip", Map.of("gc.log", "first\nsecond\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingStreamPreservesOrderAndSentinel() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", "[2.000s][info][gc] current\n[3.000s][info][gc] current end\n");
        entries.put("gc.log.0", "[0.500s][info][gc] previous\n[1.000s][info][gc] previous end\n");
        Path archive = zip("rotating.zip", entries);

        List<String> actual;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList(
                "[0.500s][info][gc] previous",
                "[1.000s][info][gc] previous end",
                "[2.000s][info][gc] current",
                "[3.000s][info][gc] current end",
                GCLogFile.END_OF_DATA_SENTINEL), actual);
    }

    @Test
    void rotatingStreamClosesCurrentSegmentAfterPartialConsumption() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", "[2.000s][info][gc] current\n[3.000s][info][gc] current end\n");
        entries.put("gc.log.0", "[0.500s][info][gc] previous\n[1.000s][info][gc] previous end\n");
        Path archive = zip("rotating-partial.zip", entries);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.500s][info][gc] previous", iterator.next());
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

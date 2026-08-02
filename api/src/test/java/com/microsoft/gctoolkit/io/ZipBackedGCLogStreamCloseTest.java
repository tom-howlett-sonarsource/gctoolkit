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

class ZipBackedGCLogStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogClosesZipAfterPartialConsumption() throws Exception {
        Path archive = zip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesZipAfterPartialConsumption() throws Exception {
        Path archive = zip("segment.zip", Map.of("segment.log", " first \nsecond\n"));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(" first ", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogClosesZipAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.001s][info][gc] old", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] old",
                "[0.500s][info][gc] old end",
                "[1.001s][info][gc] current",
                "[1.500s][info][gc] current end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path rotatingZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] old\n[0.500s][info][gc] old end\n");
        entries.put("gc.log", "[1.001s][info][gc] current\n[1.500s][info][gc] current end\n");
        return zip("rotating.zip", entries);
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

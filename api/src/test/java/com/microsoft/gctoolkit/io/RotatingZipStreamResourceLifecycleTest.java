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

class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipPreservesOrderingContentAndSentinel() throws Exception {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(List.of(
                    "[0.001s][info][gc] segment0-line1",
                    "[0.002s][info][gc] segment0-line2",
                    "[10.001s][info][gc] segment1-line1",
                    "[10.002s][info][gc] segment1-line2",
                    GCLogFile.END_OF_DATA_SENTINEL
            ), collected);
        }
    }

    @Test
    void rotatingZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            long count = lines.count();
            assertEquals(5, count);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        // Stream.flatMap (used to compose the per-segment streams) closes each segment's
        // stream as soon as it finishes supplying elements from it, even when the overall
        // stream is short-circuited. Requesting 3 of the 5 elements therefore spans both
        // zip segments while never holding more than one archive handle open at a time.
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            List<String> partial = lines.limit(3).collect(Collectors.toList());
            assertEquals(List.of(
                    "[0.001s][info][gc] segment0-line1",
                    "[0.002s][info][gc] segment0-line2",
                    "[10.001s][info][gc] segment1-line1"
            ), partial);
            assertEquals(baseline, descriptorsFor(archive), "no archive handle should remain open once the requested elements are produced");
        }

        assertEquals(baseline, descriptorsFor(archive), "closing a partially consumed stream must not leave any archive handle open");
    }

    private Path rotatingZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] segment0-line1\n[0.002s][info][gc] segment0-line2\n");
        entries.put("gc.log.1.current", "[10.001s][info][gc] segment1-line1\n[10.002s][info][gc] segment1-line2\n");
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

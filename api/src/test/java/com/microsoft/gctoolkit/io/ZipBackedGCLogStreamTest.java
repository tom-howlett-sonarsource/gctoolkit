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

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] old",
                "[0.002s][info][gc] old end",
                "[1.001s][info][gc] current",
                "[1.002s][info][gc] current end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void closingPartiallyConsumedRotatingZipClosesArchive() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> lines = stream.iterator();
            assertEquals("[0.001s][info][gc] old", lines.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] old\n[0.002s][info][gc] old end\n");
        entries.put("gc.log", "[1.001s][info][gc] current\n[1.002s][info][gc] current end\n");
        return zip("rotating.zip", entries);
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

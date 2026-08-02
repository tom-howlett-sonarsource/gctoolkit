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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.stream.Collectors.toList;

class ZipBackedGCLogStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesActiveSegmentAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.001s][info][gc] old-first", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderLinesAndSentinelWhenFullyConsumed() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);
        List<String> actual;

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            actual = lines.collect(toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] old-first",
                "[0.002s][info][gc] old-last",
                "[0.003s][info][gc] current-first",
                "[0.004s][info][gc] current-last",
                GCLogFile.END_OF_DATA_SENTINEL), actual);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.001s][info][gc] old-first\n[0.002s][info][gc] old-last\n");
        entries.put("gc.log", "[0.003s][info][gc] current-first\n[0.004s][info][gc] current-last\n");
        Path archive = temporaryDirectory.resolve("gc.zip");
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
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) count++;
                } catch (IOException ignored) {
                    // Descriptors can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

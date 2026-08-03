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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void closesCurrentArchiveSegmentWhenPartiallyConsumed() throws Exception {
        Path archive = rotatingArchive();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("[0.001s][info][gc] old", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void retainsSegmentOrderContentsAndEndOfDataSentinel() throws Exception {
        RotatingGCLogFile logFile = new RotatingGCLogFile(rotatingArchive());

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] old",
                "old detail",
                "[0.002s][info][gc] old end",
                "[1.001s][info][gc] current",
                "current detail",
                "[1.002s][info][gc] current end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc-rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, "gc.log.0", "[0.001s][info][gc] old\nold detail\n[0.002s][info][gc] old end\n");
            addEntry(output, "gc.log", "[1.001s][info][gc] current\ncurrent detail\n[1.002s][info][gc] current end\n");
        }
        return archive;
    }

    private void addEntry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
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

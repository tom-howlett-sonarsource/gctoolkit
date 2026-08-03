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
    void closeReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] older", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void retainsSegmentOrderContentsAndSentinel() throws Exception {
        try (Stream<String> lines = new RotatingGCLogFile(rotatingArchive()).stream()) {
            assertEquals(List.of(
                    "[0.001s][info][gc] older",
                    "[0.002s][info][gc] older second line",
                    "[1.001s][info][gc] current",
                    "[1.002s][info][gc] current second line",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc-rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, "gc.log.0", "[0.001s][info][gc] older\n[0.002s][info][gc] older second line\n");
            addEntry(output, "gc.log", "[1.001s][info][gc] current\n[1.002s][info][gc] current second line\n");
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

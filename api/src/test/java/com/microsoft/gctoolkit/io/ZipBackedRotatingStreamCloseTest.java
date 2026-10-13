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
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipBackedRotatingStreamCloseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void closingPartiallyConsumedRotatingStreamClosesActiveArchive() throws Exception {
        Path archive = rotatingArchive();
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = log.stream()) {
            assertEquals("[0.500s][info][gc] older start", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipStillPreservesSegmentOrderContentsAndSentinel() throws Exception {
        RotatingGCLogFile log = new RotatingGCLogFile(rotatingArchive());

        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(
                    "[0.500s][info][gc] older start",
                    "[1.000s][info][gc] older end",
                    "[1.500s][info][gc] current start",
                    "[2.000s][info][gc] current end",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            add(output, "gc.log.0", "[0.500s][info][gc] older start\n[1.000s][info][gc] older end\n");
            add(output, "gc.log", "[1.500s][info][gc] current start\n[2.000s][info][gc] current end\n");
        }
        return archive;
    }

    private static void add(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) count++;
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

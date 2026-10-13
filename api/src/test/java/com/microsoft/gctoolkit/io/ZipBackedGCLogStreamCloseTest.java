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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.stream.Collectors.toList;

class ZipBackedGCLogStreamCloseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        for (int i = 0; i < 5; i++) {
            try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
                assertEquals("[1.000s][info][gc] old", lines.findFirst().orElseThrow());
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void rotatingZipKeepsSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[1.000s][info][gc] old",
                    "old continuation",
                    "[1.500s][info][gc] old end",
                    "[2.000s][info][gc] current",
                    "current continuation",
                    "[2.500s][info][gc] current end",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, "gc.log.0", "[1.000s][info][gc] old\nold continuation\n[1.500s][info][gc] old end\n");
            addEntry(output, "gc.log", "[2.000s][info][gc] current\ncurrent continuation\n[2.500s][info][gc] current end\n");
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

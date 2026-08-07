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

class ZipBackedGCLogStreamCloseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesCurrentArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = log.stream()) {
            assertEquals("[1.000s][info][gc] old", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] old",
                "[1.500s][info][gc] old continuation",
                "[2.000s][info][gc] current",
                "[3.000s][info][gc] current continuation",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(0, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[1.000s][info][gc] old\n[1.500s][info][gc] old continuation\n");
            writeEntry(output, "gc.log", "[2.000s][info][gc] current\n[3.000s][info][gc] current continuation\n");
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String name, String contents) throws IOException {
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

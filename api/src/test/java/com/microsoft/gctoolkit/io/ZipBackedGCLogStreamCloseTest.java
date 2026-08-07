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
    void rotatingZipRetainsOrderAndSentinelAndClosesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile log = new RotatingGCLogFile(archive);

        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(
                            "[0.001s][info][gc] old-start",
                            "[1.000s][info][gc] old-end",
                            "[2.000s][info][gc] current-start",
                            "[3.000s][info][gc] current-end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }

        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = log.stream()) {
            assertEquals("[0.001s][info][gc] old-start", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void fullyConsumedSingleAndSegmentStreamsCloseTheirArchivesWhenClosed() throws Exception {
        Path archive = zip("single-and-segment.zip", "gc.log", " first \nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(List.of(" first ", "second"), lines.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, "gc.log.0", "[0.001s][info][gc] old-start\n[1.000s][info][gc] old-end\n");
            addEntry(output, "gc.log", "[2.000s][info][gc] current-start\n[3.000s][info][gc] current-end\n");
        }
        return archive;
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, entryName, contents);
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

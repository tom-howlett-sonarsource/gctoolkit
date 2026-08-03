// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeEach
    void requireFileDescriptorInspection() {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
    }

    @Test
    void singleFileStreamPreservesLinesAndClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("single.zip",
                "directory/", "",
                "directory/gc.log", " first line \n\nsecond line\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesLinesAndClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("segment.zip", "gc.log", " first line \nsecond line\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(" first line ", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingStreamPreservesSegmentOrderAndSentinelAndClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip",
                "gc.log.0", "[0.001s][info][gc] old first\n[0.002s][info][gc] old second\n",
                "gc.log", "[1.001s][info][gc] current first\n[1.002s][info][gc] current second\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] old first", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[0.001s][info][gc] old first",
                            "[0.002s][info][gc] old second",
                            "[1.001s][info][gc] current first",
                            "[1.002s][info][gc] current second",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (int index = 0; index < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
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

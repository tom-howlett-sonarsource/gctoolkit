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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipBackedGCLogStreamTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipClosesAfterPartialIterationAndPreservesContents() throws Exception {
        Path archive = zip("single.zip", "gc.log", " first \n\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("first", iterator.next());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentClosesAfterPartialIterationAndPreservesContents() throws Exception {
        Path archive = zip("segment.zip", "segment.log", " first \n\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals(" first ", iterator.next());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(List.of(" first ", "", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipClosesActiveSegmentAfterPartialIteration() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        Stream<String> lines = new RotatingGCLogFile(archive).stream();

        assertEquals(baseline, descriptorsFor(archive), "metadata discovery must close its archive streams");
        try (lines) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] old-first", iterator.next());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[1.000s][info][gc] old-first",
                            "[1.100s][info][gc] old-second",
                            "[2.000s][info][gc] current-first",
                            "[2.100s][info][gc] current-second",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip",
                "gc.log", "[2.000s][info][gc] current-first\n[2.100s][info][gc] current-second\n",
                "gc.log.1", "[1.000s][info][gc] old-first\n[1.100s][info][gc] old-second\n");
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

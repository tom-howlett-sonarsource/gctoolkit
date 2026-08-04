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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipBackedGCLogStreamLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogClosesZipAfterPartialConsumption() throws Exception {
        Path archive = writeSingleEntryZip("single.zip", "gc.log", " first \nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesEntryAndArchiveAfterPartialConsumption() throws Exception {
        Path archive = writeSingleEntryZip("segment.zip", "gc.log", " first \nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(" first ", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogClosesMetadataAndPartiallyConsumedSegmentStreams() throws Exception {
        Path archive = writeRotatingZip("partial-rotating.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(baseline, descriptorsFor(archive), "ordering metadata must not retain ZIP resources");
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] old", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogPreservesSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = writeRotatingZip("complete-rotating.zip");
        long baseline = descriptorsFor(archive);

        List<String> actual;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList(
                "[1.000s][info][gc] old",
                "[1.500s][info][gc] old detail",
                "[2.000s][info][gc] current",
                "[2.500s][info][gc] current detail",
                GCLogFile.END_OF_DATA_SENTINEL), actual);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path writeRotatingZip(String name) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[1.000s][info][gc] old\n[1.500s][info][gc] old detail\n");
            writeEntry(output, "gc.log", "[2.000s][info][gc] current\n[2.500s][info][gc] current detail\n");
        }
        return archive;
    }

    private Path writeSingleEntryZip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, entryName, contents);
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
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString()))
                        count++;
                } catch (IOException ignored) {
                    // File descriptors can disappear while /proc is traversed.
                }
            }
        }
        return count;
    }
}

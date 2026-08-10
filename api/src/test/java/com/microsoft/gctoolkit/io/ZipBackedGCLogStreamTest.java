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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipBackedGCLogStreamTest {

    private static final String OLD_START = "[0.000s][info][gc] old start";
    private static final String OLD_END = "[1.000s][info][gc] old end";
    private static final String CURRENT_START = "[2.000s][info][gc] current start";
    private static final String CURRENT_END = "[3.000s][info][gc] current end";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = singleZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(OLD_START, lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentReleasesArchiveWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = singleZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(OLD_START, lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipPreservesLinesAndEndOfDataSentinel() throws Exception {
        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(singleZip()).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(OLD_START, OLD_END, GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void rotatingZipReleasesArchivesWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(OLD_START, lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderLinesAndEndOfDataSentinel() throws Exception {
        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(rotatingZip()).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(OLD_START, OLD_END, CURRENT_START, CURRENT_END,
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path singleZip() throws IOException {
        Path archive = temporaryDirectory.resolve("single-" + System.nanoTime() + ".zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log", OLD_START + "\n" + OLD_END + "\n");
        }
        return archive;
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating-" + System.nanoTime() + ".zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", OLD_START + "\n" + OLD_END + "\n");
            writeEntry(output, "gc.log", CURRENT_START + "\n" + CURRENT_END + "\n");
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

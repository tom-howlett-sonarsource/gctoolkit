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
    void singleZipPreservesProcessedLinesAndEndOfDataSentinel() throws Exception {
        Path archive = zip("single.zip",
                "gc.log", "  first line  \n\nsecond line\n");

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void zipSegmentPreservesUnprocessedLineContents() throws Exception {
        Path archive = zip("segment.zip",
                "segment.log", "  first line  \n\nsecond line\n");

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("  first line  ", "", "second line"), lines);
    }

    @Test
    void rotatingZipClosesActiveSegmentAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(baseline, descriptorsFor(archive), "metadata scans must close their ZIP files");
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] old first", iterator.next());
            assertTrue(descriptorsFor(archive) > baseline, "the active segment must still be open");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderAndEndOfDataSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] old first",
                "[1.100s][info][gc] old last",
                "[2.000s][info][gc] current first",
                "[2.100s][info][gc] current last",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip",
                "gc.log.0", "[1.000s][info][gc] old first\n[1.100s][info][gc] old last\n",
                "gc.log", "[2.000s][info][gc] current first\n[2.100s][info][gc] current last\n");
    }

    private Path zip(String name, String... entriesAndContents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (int i = 0; i < entriesAndContents.length; i += 2) {
                output.putNextEntry(new ZipEntry(entriesAndContents[i]));
                output.write(entriesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
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

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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamResourceReleaseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesAllArchiveHandlesAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip",
                "gc.log.0", "[0.001s][info][gc] zero\n[0.500s][info][gc] half\n",
                "gc.log", "[1.001s][info][gc] one\n[1.500s][info][gc] more\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertTrue(iterator.hasNext());
            assertEquals("[0.001s][info][gc] zero", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderingAndSentinelAndReleasesHandles() throws Exception {
        Path archive = zip("ordered.zip",
                "gc.log", "[1.001s][info][gc] one\n[1.500s][info][gc] more\n",
                "gc.log.0", "[0.001s][info][gc] zero\n[0.500s][info][gc] half\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] zero", "[0.500s][info][gc] half",
                "[1.001s][info][gc] one", "[1.500s][info][gc] more", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipFullyConsumedKeepsContentsAndSentinel() throws Exception {
        Path archive = zip("full.zip", "gc.log", "  first  \n\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), stream.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleGZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write("[0.001s][info][gc] first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeakHandles() throws Exception {
        Path archive = zip("times.zip", "segment.log", "[0.001s][info][gc] first\n[2.500s][info][gc] last\n");
        long baseline = descriptorsFor(archive);

        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "segment.log");
        assertEquals(0.001d, segment.getStartTime(), 1e-9);
        assertEquals(2.5d, segment.getEndTime(), 1e-9);

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithMissingEntryIsEmptyAndReleasesArchive() throws Exception {
        Path archive = zip("missing.zip", "segment.log", "line\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "absent.log").stream()) {
            assertEquals(0, lines.count());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String... entriesAndContents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
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
                    if (Files.readSymbolicLink(descriptor).toString().replace(" (deleted)", "").equals(expected.toString()))
                        count++;
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipBackedGCLogStreamBehaviorTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleStreamCloseReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("single-close.zip", entries("gc.log", "first\nsecond\n"));
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open while its stream is open");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentStreamCloseReleasesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("segment-close.zip", entries("gc.log", "first\nsecond\n"));
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open while its stream is open");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingStreamCloseReleasesArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip("rotating-close.zip");
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertTrue(iterator.hasNext());
            assertEquals("[0.001s][info][gc] old first", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipBackedStreamsPreserveContentsOrderingAndSentinel() throws Exception {
        Path singleArchive = zip("single-content.zip", entries("gc.log", " first \n\nsecond\n"));
        try (Stream<String> lines = new SingleGCLogFile(singleArchive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }

        Path segmentArchive = zip("segment-content.zip", entries("gc.log", " first \n\nsecond\n"));
        try (Stream<String> lines = new GCLogFileZipSegment(segmentArchive, "gc.log").stream()) {
            assertEquals(List.of(" first ", "", "second"), lines.collect(Collectors.toList()));
        }

        Path rotatingArchive = rotatingZip("rotating-content.zip");
        try (Stream<String> lines = new RotatingGCLogFile(rotatingArchive).stream()) {
            assertEquals(List.of(
                            "[0.001s][info][gc] old first",
                            "[0.002s][info][gc] old second",
                            "[0.003s][info][gc] current first",
                            "[0.004s][info][gc] current second",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingZip(String name) throws IOException {
        LinkedHashMap<String, String> contents = new LinkedHashMap<>();
        contents.put("gc.log.0", "[0.001s][info][gc] old first\n[0.002s][info][gc] old second\n");
        contents.put("gc.log", "[0.003s][info][gc] current first\n[0.004s][info][gc] current second\n");
        return zip(name, contents);
    }

    private LinkedHashMap<String, String> entries(String entryName, String contents) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put(entryName, contents);
        return entries;
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(FILE_DESCRIPTORS)) {
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

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesFilteredLinesAndEndOfDataSentinel() throws Exception {
        Path archive = zip("single.zip", entries(
                "gc.log", "  first line  \n\n second line \n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentPreservesUnprocessedLineContents() throws Exception {
        Path archive = zip("segment.zip", entries(
                "gc.log.0", "  first line  \n\n second line \n"));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals(List.of("  first line  ", "", " second line "),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipPreservesSegmentOrderAndEndOfDataSentinel() throws Exception {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[1.000s][info][gc] older segment start",
                            "[1.500s][info][gc] older segment end",
                            "[2.000s][info][gc] current segment start",
                            "[2.500s][info][gc] current segment end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipClosesAllArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[1.000s][info][gc] older segment start", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip", entries(
                "gc.log.0", "[1.000s][info][gc] older segment start\n[1.500s][info][gc] older segment end\n",
                "gc.log", "[2.000s][info][gc] current segment start\n[2.500s][info][gc] current segment end\n"));
    }

    private LinkedHashMap<String, String> entries(String... namesAndContents) {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        for (int index = 0; index < namesAndContents.length; index += 2) {
            entries.put(namesAndContents[index], namesAndContents[index + 1]);
        }
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

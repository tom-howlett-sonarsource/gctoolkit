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

class ZipGCLogStreamClosureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] oldest", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderLinesAndEndSentinel() throws Exception {
        Path archive = rotatingArchive();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] oldest",
                "[1.500s][info][gc] oldest detail",
                "[2.000s][info][gc] middle",
                "[2.500s][info][gc] middle detail",
                "[3.000s][info][gc] current",
                "[3.500s][info][gc] current detail",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void singleZipPreservesLinesAndEndSentinel() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", " first line \n\nsecond line\n");
        Path archive = zip("single-content.zip", entries);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void zipSegmentPreservesLines() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("segment.log", " first line \n\nsecond line\n");
        Path archive = zip("segment-content.zip", entries);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(" first line ", "", "second line"), lines);
    }

    private Path rotatingArchive() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.2", "[1.000s][info][gc] oldest\n[1.500s][info][gc] oldest detail\n");
        entries.put("gc.log.1", "[2.000s][info][gc] middle\n[2.500s][info][gc] middle detail\n");
        entries.put("gc.log", "[3.000s][info][gc] current\n[3.500s][info][gc] current detail\n");
        return zip("rotating.zip", entries);
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

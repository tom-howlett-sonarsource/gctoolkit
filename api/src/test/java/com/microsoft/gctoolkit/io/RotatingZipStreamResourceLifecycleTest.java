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

class RotatingZipStreamResourceLifecycleTest {

    private static final String OLDEST = "[1.000s][info][gc] oldest";
    private static final String OLD_TAIL = "[1.100s][info][gc] old tail";
    private static final String MIDDLE = "[2.000s][info][gc] middle";
    private static final String MIDDLE_TAIL = "[2.100s][info][gc] middle tail";
    private static final String NEWEST = "[3.000s][info][gc] newest";
    private static final String NEWEST_TAIL = "[3.100s][info][gc] newest tail";

    @TempDir
    Path temporaryDirectory;

    @Test
    void partiallyConsumedRotatingZipStreamClosesAllArchiveResources() throws Exception {
        Path archive = rotatingZip("partial.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(baseline, descriptorsFor(archive), "metadata probes must release their archives");
            assertEquals(OLDEST, lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentMetadataProbesCloseTheirArchiveResources() throws Exception {
        Path archive = rotatingZip("metadata.zip");
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.1");

        assertEquals(2.0d, segment.getStartTime());
        assertEquals(2.1d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipKeepsSegmentOrderContentsAndEndSentinel() throws Exception {
        Path archive = rotatingZip("complete.zip");
        long baseline = descriptorsFor(archive);
        List<String> lines;

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(OLDEST, OLD_TAIL, MIDDLE, MIDDLE_TAIL, NEWEST, NEWEST_TAIL,
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip(String name) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", OLDEST + "\n" + OLD_TAIL + "\n");
            writeEntry(output, "gc.log.1", MIDDLE + "\n" + MIDDLE_TAIL + "\n");
            writeEntry(output, "gc.log", NEWEST + "\n" + NEWEST_TAIL + "\n");
        }
        return archive;
    }

    private static void writeEntry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static long descriptorsFor(Path archive) throws IOException {
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

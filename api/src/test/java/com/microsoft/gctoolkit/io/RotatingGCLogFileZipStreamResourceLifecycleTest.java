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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the composed stream returned by {@link RotatingGCLogFile} for a ZIP-backed
 * rotating log both preserves segment ordering/content/end-of-data sentinel, and releases
 * every underlying per-segment archive handle when the returned stream is closed - whether
 * it was drained fully or only partially consumed (including partial consumption that stops
 * midway through a later segment, after earlier segments have already been drained).
 */
class RotatingGCLogFileZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    private static final String FIRST_LINE = "[0.001s][info][gc] first";
    private static final String SECOND_LINE = "[0.002s][info][gc] second";
    private static final String THIRD_LINE = "[10.001s][info][gc] third";
    private static final String FOURTH_LINE = "[10.002s][info][gc] fourth";

    @Test
    void composedStreamPreservesSegmentOrderingContentAndSentinel() throws Exception {
        Path archive = rotatingZip("ordering.zip");

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(FIRST_LINE, SECOND_LINE, THIRD_LINE, FOURTH_LINE, GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void composedStreamClosesEveryArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip("full.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            long count = stream.count();
            assertEquals(5, count); // 4 lines + end-of-data sentinel
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void composedStreamClosesEveryArchiveAfterPartialConsumptionAcrossSegments() throws Exception {
        Path archive = rotatingZip("partial.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            // Pulls past the whole first segment and into the second segment, then
            // stops without exhausting it or reaching the end-of-data sentinel -
            // exercising both a segment that finished naturally and one abandoned mid-read.
            Iterator<String> iterator = stream.iterator();
            List<String> collected = new ArrayList<>();
            collected.add(iterator.next());
            collected.add(iterator.next());
            collected.add(iterator.next());

            assertEquals(List.of(FIRST_LINE, SECOND_LINE, THIRD_LINE), collected);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip(String name) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "app.log.0", FIRST_LINE + "\n" + SECOND_LINE + "\n");
            writeEntry(output, "app.log.1.current", THIRD_LINE + "\n" + FOURTH_LINE + "\n");
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
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

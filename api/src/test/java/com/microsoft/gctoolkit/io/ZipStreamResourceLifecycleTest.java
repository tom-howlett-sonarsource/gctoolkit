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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Additional coverage for zip/gzip backed GC log streams: line content, rotating-segment
 * ordering, the end-of-data sentinel, and archive resource release on full and partial
 * consumption. {@link VisibleZipStreamResourceLifecycleTest} already covers the basic
 * partial-consumption-then-close case for {@link SingleGCLogFile} and {@link GCLogFileZipSegment};
 * this file adds the remaining scenarios, including {@link RotatingGCLogFile}.
 */
class ZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGZipFilePreservesContentAndReleasesResourcesOnFullConsumption() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipFilePreservesContentAndReleasesResourcesOnFullConsumption() throws Exception {
        Path archive = zip("single.zip", "gc.log", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesLineContentAndReleasesResourcesOnFullConsumption() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipFilePreservesSegmentOrderingSentinelAndReleasesResources() throws Exception {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "app.log", "[100.000s][info][gc] current segment line1\n[200.000s][info][gc] current segment line2\n");
            writeEntry(output, "app.log.0", "[0.001s][info][gc] older segment line1\n[50.000s][info][gc] older segment line2\n");
        }
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // Older segment (app.log.0) content must precede the current segment (app.log) content,
        // and the composed stream must still terminate with the end-of-data sentinel.
        assertEquals(List.of(
                "[0.001s][info][gc] older segment line1",
                "[50.000s][info][gc] older segment line2",
                "[100.000s][info][gc] current segment line1",
                "[200.000s][info][gc] current segment line2",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipFileReleasesResourcesWhenClosedAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("rotating-partial.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "app.log", "[100.000s][info][gc] current segment line1\n[200.000s][info][gc] current segment line2\n");
            writeEntry(output, "app.log.0", "[0.001s][info][gc] older segment line1\n[50.000s][info][gc] older segment line2\n");
        }
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] older segment line1", stream.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, entryName, contents);
        }
        return archive;
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
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

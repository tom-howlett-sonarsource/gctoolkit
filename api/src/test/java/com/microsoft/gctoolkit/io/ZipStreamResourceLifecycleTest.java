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
 * Additional coverage for archive resource release, complementing
 * {@link VisibleZipStreamResourceLifecycleTest}: gzip-backed single log files, the
 * composed stream produced by {@link RotatingGCLogFile} over a multi-segment zip, and
 * content/ordering/sentinel preservation across all of the above.
 */
class ZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipStreamPreservesLineContentsAndSentinel() throws Exception {
        Path archive = zip("single.zip", "gc.log", "[0.001s][info][gc] first\n\n  [0.002s][info][gc] second  \n");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(
                    List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentStreamPreservesLineContentsAndClosesAfterFullConsumption() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "[0.001s][info][gc] first\nsecond\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(List.of("[0.001s][info][gc] first", "second"), lines.collect(Collectors.toList()));
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipComposedStreamPreservesSegmentOrderingAndSentinel() throws Exception {
        Path archive = rotatingZip(
                "rotating.zip",
                "[0.001s][info][gc] old-first\n[0.002s][info][gc] old-second\n",
                "[10.001s][info][gc] new-first\n[10.002s][info][gc] new-second\n");

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(
                    List.of(
                            "[0.001s][info][gc] old-first",
                            "[0.002s][info][gc] old-second",
                            "[10.001s][info][gc] new-first",
                            "[10.002s][info][gc] new-second",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipComposedStreamReleasesArchiveResourcesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip(
                "rotating-partial.zip",
                "[0.001s][info][gc] old-first\n[0.002s][info][gc] old-second\n",
                "[10.001s][info][gc] new-first\n[10.002s][info][gc] new-second\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.001s][info][gc] old-first", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipComposedStreamReleasesArchiveResourcesAfterFullConsumption() throws Exception {
        Path archive = rotatingZip(
                "rotating-full.zip",
                "[0.001s][info][gc] old-first\n[0.002s][info][gc] old-second\n",
                "[10.001s][info][gc] new-first\n[10.002s][info][gc] new-second\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            lines.forEach(line -> { /* drain fully */ });
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private Path rotatingZip(String name, String olderSegmentContents, String currentSegmentContents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write(olderSegmentContents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.1.current"));
            output.write(currentSegmentContents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
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

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

/**
 * Covers the composed stream returned by {@link RotatingGCLogFile} when the rotating log is
 * backed by a single Zip archive containing multiple segments (as produced by
 * {@link GCLogFileZipSegment}). Verifies that segment ordering, line content, and the
 * end-of-data sentinel are preserved, and that no archive file descriptor survives either full
 * or partial consumption once the returned stream is closed.
 */
class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipPreservesOrderContentAndSentinelWithoutLeakingDescriptors() throws Exception {
        Path archive = rotatingZipArchive("rotating.zip");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(
                List.of(
                        "[0.001s][info][gc] segment-oldest-first",
                        "[0.002s][info][gc] segment-oldest-second",
                        "[2.001s][info][gc] segment-middle-first",
                        "[2.002s][info][gc] segment-middle-second",
                        "[4.001s][info][gc] segment-current-first",
                        "[4.002s][info][gc] segment-current-second",
                        GCLogFile.END_OF_DATA_SENTINEL),
                lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveHandlesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZipArchive("rotating-partial.zip");
        long baseline = descriptorsFor(archive);

        List<String> firstTwo;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            firstTwo = stream.limit(2).collect(Collectors.toList());
        }

        assertEquals(
                List.of("[0.001s][info][gc] segment-oldest-first", "[0.002s][info][gc] segment-oldest-second"),
                firstTwo);
        assertEquals(baseline, descriptorsFor(archive));
    }

    /**
     * Builds a single Zip archive with three rotating-log entries: an oldest and a middle
     * segment named with a numeric rotation suffix, and a "current" segment with no suffix,
     * matching the naming convention {@link RotatingLogFileMetadata} expects.
     */
    private Path rotatingZipArchive(String archiveName) throws IOException {
        Path archive = temporaryDirectory.resolve(archiveName);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.1", "[0.001s][info][gc] segment-oldest-first\n[0.002s][info][gc] segment-oldest-second\n");
            writeEntry(output, "gc.log.0", "[2.001s][info][gc] segment-middle-first\n[2.002s][info][gc] segment-middle-second\n");
            writeEntry(output, "gc.log", "[4.001s][info][gc] segment-current-first\n[4.002s][info][gc] segment-current-second\n");
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

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

import static com.microsoft.gctoolkit.io.GCLogFile.END_OF_DATA_SENTINEL;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that the composed {@code Stream<String>} returned by {@link RotatingGCLogFile} for a
 * multi-entry ZIP archive releases every underlying archive resource when it is closed, including
 * when a caller closes a partially consumed stream, while preserving line contents, rotating-segment
 * ordering, and the end-of-data sentinel.
 */
class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipPreservesContentsOrderingAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> expected = expectedOrderedLines(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        // Contents and rotating-segment ordering are preserved, terminated by the sentinel.
        assertEquals(expected, lines.subList(0, lines.size() - 1));
        assertEquals(END_OF_DATA_SENTINEL, lines.get(lines.size() - 1));
    }

    @Test
    void rotatingZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            stream.forEach(line -> { /* drain every segment */ });
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        // Pull a single element, then close the composed stream without consuming the remainder.
        Stream<String> stream = new RotatingGCLogFile(archive).stream();
        try {
            assertEquals(expectedOrderedLines(archive).get(0), stream.iterator().next());
        } finally {
            stream.close();
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    /** The lines the composed stream is expected to emit, in rotating-segment order. */
    private List<String> expectedOrderedLines(Path archive) throws IOException {
        return new RotatingGCLogFile(archive).getMetaData().logFiles()
                .flatMap(LogFileSegment::stream)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[0.001s][info][gc] zero-a\n[0.002s][info][gc] zero-b\n");
            writeEntry(output, "gc.log", "[1.001s][info][gc] one-a\n[1.002s][info][gc] one-b\n");
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

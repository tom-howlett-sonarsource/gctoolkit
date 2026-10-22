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
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipComposedStreamClosesAllArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            // Consume only the first line; the remaining segments remain open.
            assertEquals("[1.000s][info][gc] segment0-a", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe at least one open archive");
        }

        assertEquals(baseline, descriptorsFor(archive),
                "closing the composed stream must release every underlying archive descriptor");
    }

    @Test
    void rotatingZipComposedStreamPreservesOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] segment0-a",
                "[2.000s][info][gc] segment0-b",
                "[3.000s][info][gc] segment1-a",
                "[4.000s][info][gc] segment1-b",
                "[5.000s][info][gc] current-a",
                "[6.000s][info][gc] current-b",
                END_OF_DATA_SENTINEL
        ), lines);
    }

    /**
     * Builds a single zip archive holding three rotating segments. The two numbered entries are the
     * rotated-out segments and the un-numbered {@code gc.log} entry is the current file. The ordering
     * logic reconstructs chronological order from the timestamps, yielding segment0, segment1, current.
     */
    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[1.000s][info][gc] segment0-a\n[2.000s][info][gc] segment0-b\n");
            writeEntry(output, "gc.log.1", "[3.000s][info][gc] segment1-a\n[4.000s][info][gc] segment1-b\n");
            writeEntry(output, "gc.log", "[5.000s][info][gc] current-a\n[6.000s][info][gc] current-b\n");
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

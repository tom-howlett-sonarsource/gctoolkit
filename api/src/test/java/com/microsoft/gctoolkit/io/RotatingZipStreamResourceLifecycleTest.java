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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the composed {@link Stream} returned by {@link RotatingGCLogFile} over a ZIP archive
 * releases every underlying archive resource when closed, preserves line contents, rotating-segment
 * ordering, and the end-of-data sentinel.
 */
class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        // Open the composed stream, read a single line, then close it without draining the rest.
        // Closing a partially consumed stream must release every underlying archive handle.
        Stream<String> lines = new RotatingGCLogFile(archive).stream();
        try {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.100s][info][gc] older-first", iterator.next());
            assertTrue(iterator.hasNext(), "consumption must be partial for this test to be meaningful");
        } finally {
            lines.close();
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(baseline, descriptorsFor(archive));

        // End-of-data sentinel is preserved and marks the end of the data.
        assertEquals(GCLogFile.END_OF_DATA_SENTINEL, lines.get(lines.size() - 1));

        // Line contents are preserved (trimmed, blank lines removed).
        assertTrue(lines.contains("[0.100s][info][gc] older-first"));
        assertTrue(lines.contains("[0.101s][info][gc] older-second"));
        assertTrue(lines.contains("[5.100s][info][gc] current-first"));
        assertTrue(lines.contains("[5.101s][info][gc] current-second"));

        // Rotating-segment ordering is preserved: the older segment precedes the current segment,
        // and lines within a segment keep their relative order.
        assertTrue(lines.indexOf("[0.100s][info][gc] older-first") < lines.indexOf("[0.101s][info][gc] older-second"));
        assertTrue(lines.indexOf("[0.101s][info][gc] older-second") < lines.indexOf("[5.100s][info][gc] current-first"));
        assertTrue(lines.indexOf("[5.100s][info][gc] current-first") < lines.indexOf("[5.101s][info][gc] current-second"));
    }

    /**
     * Builds a two-entry ZIP representing a rotating GC log. The {@code gc.log.0} entry is the older
     * segment and the {@code gc.log} entry is the current segment; timestamps make the ordering
     * deterministic.
     */
    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[0.100s][info][gc] older-first\n[0.101s][info][gc] older-second\n");
            writeEntry(output, "gc.log", "[5.100s][info][gc] current-first\n[5.101s][info][gc] current-second\n");
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

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipBackedGCLogStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void closesSingleFileArchiveWhenPartiallyConsumed() throws Exception {
        Path archive = createZip("single.zip", List.of(
                entry("gc.log", "  first  \n\nsecond\n")));
        assertPartiallyConsumedStreamClosesArchive(
                archive, () -> new SingleGCLogFile(archive).stream(), "first");
    }

    @Test
    void closesZipSegmentArchiveWhenPartiallyConsumed() throws Exception {
        Path archive = createZip("segment.zip", List.of(
                entry("segment.log", "first\nsecond\n")));
        assertPartiallyConsumedStreamClosesArchive(
                archive, () -> new GCLogFileZipSegment(archive, "segment.log").stream(), "first");
    }

    @Test
    void rotatingStreamClosesActiveArchiveAndPreservesDataContract() throws Exception {
        Path archive = createZip("rotating.zip", List.of(
                entry("gc.log.0", "[0.001s][info][gc] old\n[0.002s][info][gc] old end\n"),
                entry("gc.log", "[1.001s][info][gc] current\n[1.002s][info][gc] current end\n")));
        RotatingGCLogFile log = new RotatingGCLogFile(archive);

        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = log.stream()) {
            assertEquals("[0.001s][info][gc] old", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = log.stream()) {
            assertEquals(List.of(
                            "[0.001s][info][gc] old",
                            "[0.002s][info][gc] old end",
                            "[1.001s][info][gc] current",
                            "[1.002s][info][gc] current end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(0, descriptorsFor(archive));
    }

    private void assertPartiallyConsumedStreamClosesArchive(
            Path archive, StreamSupplier streamSupplier, String expectedFirstLine) throws IOException {
        long baseline = descriptorsFor(archive);
        try (Stream<String> closeableLines = streamSupplier.get()) {
            assertEquals(expectedFirstLine, closeableLines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open while its stream is active");
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path createZip(String name, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private ArchiveEntry entry(String name, String contents) {
        return new ArchiveEntry(name, contents);
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
                    // Descriptors can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }

    private static final class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    @FunctionalInterface
    private interface StreamSupplier {
        Stream<String> get() throws IOException;
    }
}

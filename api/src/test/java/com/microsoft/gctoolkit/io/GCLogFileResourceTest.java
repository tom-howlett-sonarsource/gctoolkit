// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileResourceTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                new ArchiveEntry("gc.log", " first line \n\nsecond line\n")));
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(toList()));
        }
        assertArchiveClosed(archive);

        Stream<String> lines = logFile.stream();
        try {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertArchiveOpenWhenObservable(archive);
        } finally {
            lines.close();
        }

        assertArchiveClosed(archive);
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                new ArchiveEntry("gc.log.0", "[1.000s][info][gc] first\n[1.500s][info][gc] second\n")));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");

        Stream<String> lines = segment.stream();
        try {
            assertEquals("[1.000s][info][gc] first", lines.findFirst().orElseThrow());
            assertArchiveOpenWhenObservable(archive);
        } finally {
            lines.close();
        }

        assertArchiveClosed(archive);
    }

    @Test
    void rotatingZipStreamPreservesOrderAndClosesAfterPartialConsumption() throws IOException {
        Path archive = createZip("rotating.zip", List.of(
                new ArchiveEntry("gc.log", "[2.000s][info][gc] current first \n[2.500s][info][gc] current second\n"),
                new ArchiveEntry("gc.log.0", "[1.000s][info][gc] older first\n[1.500s][info][gc] older second\n")));
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of(
                    "[1.000s][info][gc] older first",
                    "[1.500s][info][gc] older second",
                    "[2.000s][info][gc] current first",
                    "[2.500s][info][gc] current second",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
        assertArchiveClosed(archive);

        Stream<String> partialLines = logFile.stream();
        try {
            Iterator<String> iterator = partialLines.iterator();
            assertEquals("[1.000s][info][gc] older first", iterator.next());
        } finally {
            partialLines.close();
        }

        assertArchiveClosed(archive);
    }

    @Test
    void streamCloseWrapsResourceIOException() {
        Stream<String> lines = StreamResources.closeWith(Stream.empty(), () -> {
            throw new IOException("close failed");
        });

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, lines::close);

        assertEquals("close failed", exception.getCause().getMessage());
    }

    private Path createZip(String fileName, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private void assertArchiveOpenWhenObservable(Path archive) throws IOException {
        if (Files.isDirectory(FILE_DESCRIPTORS)) {
            assertTrue(openDescriptorsFor(archive) > 0);
        }
    }

    private void assertArchiveClosed(Path archive) throws IOException {
        if (Files.isDirectory(FILE_DESCRIPTORS)) {
            assertEquals(0, openDescriptorsFor(archive));
        } else {
            Path movedArchive = archive.resolveSibling(archive.getFileName() + ".moved");
            Files.move(archive, movedArchive);
            Files.move(movedArchive, archive);
        }
    }

    private long openDescriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        try (Stream<Path> descriptors = Files.list(FILE_DESCRIPTORS)) {
            return descriptors
                    .filter(Files::isSymbolicLink)
                    .map(GCLogFileResourceTest::readSymbolicLink)
                    .filter(expected::equals)
                    .count();
        }
    }

    private static Path readSymbolicLink(Path descriptor) {
        try {
            return Files.readSymbolicLink(descriptor);
        } catch (IOException exception) {
            return Path.of("");
        }
    }

    private static class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogFileStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                entry("gc.log", "  first line  \n\nsecond line\n"),
                entry("ignored.log", "ignored\n")));
        long initiallyOpen = openArchiveDescriptors(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertTrue(openArchiveDescriptors(archive) > initiallyOpen);
        }

        assertEquals(initiallyOpen, openArchiveDescriptors(archive));
    }

    @Test
    void zipSegmentClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(entry("gc.log.0", "first line\nsecond line\n")));
        long initiallyOpen = openArchiveDescriptors(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertTrue(openArchiveDescriptors(archive) > initiallyOpen);
        }

        assertEquals(initiallyOpen, openArchiveDescriptors(archive));
    }

    @Test
    void zipSegmentClosesZipWhenTheEntryIsMissing() throws IOException {
        Path archive = createZip("missing-segment.zip", List.of(entry("gc.log.0", "first line\n")));
        long initiallyOpen = openArchiveDescriptors(archive);

        assertThrows(RuntimeException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());

        assertEquals(initiallyOpen, openArchiveDescriptors(archive));
    }

    @Test
    void rotatingGCLogFileClosesAllZipResourcesAfterPartialConsumption() throws IOException {
        Path archive = rotatingArchive();
        long initiallyOpen = openArchiveDescriptors(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("1.000: old first", lines.findFirst().orElseThrow());
        }

        assertEquals(initiallyOpen, openArchiveDescriptors(archive));
    }

    @Test
    void rotatingGCLogFilePreservesOrderedLinesAndSentinel() throws IOException {
        Path archive = rotatingArchive();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "1.000: old first",
                    "2.000: old second",
                    "3.000: current first",
                    "4.000: current second",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void resourceStreamClosesEveryResourceWhenCloseFails() {
        IOException readerFailure = new IOException("reader close failed");
        IOException archiveFailure = new IOException("archive close failed");
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw readerFailure;
            }
        };
        Closeable archive = () -> {
            throw archiveFailure;
        };

        UncheckedIOException exception = assertThrows(UncheckedIOException.class,
                () -> ResourceStreams.lines(reader, archive).close());

        assertSame(readerFailure, exception.getCause());
        assertSame(archiveFailure, readerFailure.getSuppressed()[0]);
    }

    @Test
    void closeFailureIsSuppressedOnOriginalFailure() {
        IOException originalFailure = new IOException("read failed");
        IOException closeFailure = new IOException("close failed");

        ResourceStreams.closeAfterFailure(originalFailure, () -> {
            throw closeFailure;
        }, null);

        assertSame(closeFailure, originalFailure.getSuppressed()[0]);
    }

    private Path rotatingArchive() throws IOException {
        return createZip("rotating.zip", List.of(
                entry("gc.log.0", "1.000: old first\n2.000: old second\n"),
                entry("gc.log", "3.000: current first  \n\n4.000: current second\n")));
    }

    private Path createZip(String name, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private static ArchiveEntry entry(String name, String contents) {
        return new ArchiveEntry(name, contents);
    }

    private static long openArchiveDescriptors(Path archive) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptors), "File descriptor checks require /proc/self/fd");
        Path realArchive = archive.toRealPath();
        try (Stream<Path> openDescriptors = Files.list(descriptors)) {
            return openDescriptors.filter(descriptor -> isSameFile(descriptor, realArchive)).count();
        }
    }

    private static boolean isSameFile(Path descriptor, Path archive) {
        try {
            return Files.isSameFile(descriptor, archive);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

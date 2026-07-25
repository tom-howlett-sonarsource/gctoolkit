// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
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

class ZipGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                new ArchiveEntry("gc.log", " first line \n\n second line \n")));
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        Stream<String> lines = logFile.stream();
        try {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertArchiveIsOpen(archive);
        } finally {
            lines.close();
        }

        assertArchiveIsClosed(archive);
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                new ArchiveEntry("gc.log", "first line\nsecond line\n")));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        Stream<String> lines = segment.stream();
        try {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertArchiveIsOpen(archive);
        } finally {
            lines.close();
        }

        assertArchiveIsClosed(archive);
    }

    @Test
    void rotatingLogClosesArchivesAfterPartialConsumption() throws IOException {
        Path archive = createZip("rotating.zip", List.of(
                new ArchiveEntry("gc.log.0", "[1.000s][info][gc] older segment\n[1.500s][info][gc] older tail\n"),
                new ArchiveEntry("gc.log", "[2.000s][info][gc] current segment\n[2.500s][info][gc] current tail\n")));
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        Stream<String> lines = logFile.stream();
        try {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] older segment", iterator.next());
        } finally {
            lines.close();
        }

        assertArchiveIsClosed(archive);
    }

    @Test
    void rotatingLogPreservesSegmentOrderLinesAndSentinel() throws IOException {
        Path archive = createZip("ordered.zip", List.of(
                new ArchiveEntry("gc.log.0", "[1.000s][info][gc] older segment\n[1.500s][info][gc] older tail\n\n"),
                new ArchiveEntry("gc.log", "[2.000s][info][gc] current segment\n[2.500s][info][gc] current tail\n")));
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of(
                    "[1.000s][info][gc] older segment",
                    "[1.500s][info][gc] older tail",
                    "[2.000s][info][gc] current segment",
                    "[2.500s][info][gc] current tail",
                    logFile.endOfData()), lines.collect(Collectors.toList()));
        }

        assertArchiveIsClosed(archive);
    }

    @Test
    void closeableLineStreamClosesEveryResourceWhenClosingFails() {
        IOException readerFailure = new IOException("reader");
        IOException archiveFailure = new IOException("archive");
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw readerFailure;
            }
        };
        Closeable archive = () -> {
            throw archiveFailure;
        };

        Stream<String> lines = CloseableStreams.lines(reader, archive);
        assertEquals("line", lines.findFirst().orElseThrow());
        UncheckedIOException exception = assertThrows(UncheckedIOException.class, lines::close);

        assertSame(readerFailure, exception.getCause());
        assertSame(archiveFailure, exception.getCause().getSuppressed()[0]);
    }

    private Path createZip(String fileName, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (ArchiveEntry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private void assertArchiveIsOpen(Path archive) throws IOException {
        assertTrue(openArchiveDescriptors(archive) > 0, "Expected an open descriptor for " + archive);
    }

    private void assertArchiveIsClosed(Path archive) throws IOException {
        assertEquals(0, openArchiveDescriptors(archive), "Expected no open descriptors for " + archive);
    }

    private long openArchiveDescriptors(Path archive) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptors), "File descriptor assertions require /proc/self/fd");
        try (Stream<Path> openDescriptors = Files.list(descriptors)) {
            return openDescriptors.filter(descriptor -> isSameFile(descriptor, archive)).count();
        }
    }

    private boolean isSameFile(Path descriptor, Path archive) {
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

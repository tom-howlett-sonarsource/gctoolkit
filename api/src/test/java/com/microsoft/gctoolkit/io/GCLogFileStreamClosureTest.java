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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogFileStreamClosureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                new Entry("gc.log", " first line \nsecond line\n")));
        Stream<String> stream = new SingleGCLogFile(archive).stream();

        Iterator<String> lines = stream.iterator();
        assertEquals("first line", lines.next());
        assertArchiveIsOpen(archive);

        stream.close();

        assertArchiveIsClosed(archive);
    }

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path archive = createZip("single-content.zip", List.of(
                new Entry("gc.log", " first line \n\nsecond line\n")));

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    stream.collect(Collectors.toList()));
        }

        assertArchiveIsClosed(archive);
    }

    @Test
    void singleGzipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = temporaryDirectory.resolve("single.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
        }
        Stream<String> stream = new SingleGCLogFile(archive).stream();

        assertEquals("first line", stream.iterator().next());
        assertArchiveIsOpen(archive);

        stream.close();

        assertArchiveIsClosed(archive);
    }

    @Test
    void singleZipStreamClosesArchiveWhenOpeningEntryFails() throws IOException {
        Path archive = temporaryDirectory.resolve("invalid.zip");
        Files.write(archive, new byte[] {
                0x50, 0x4b, 0x03, 0x04,
                0x14, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00,
                0x00, 0x00, 0x78});

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertArchiveIsClosed(archive);
    }

    @Test
    void singleGzipStreamClosesArchiveWhenHeaderIsInvalid() throws IOException {
        Path archive = temporaryDirectory.resolve("invalid.gz");
        Files.write(archive, new byte[] {0x1f, (byte) 0x8b, 0x00});

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertArchiveIsClosed(archive);
    }

    @Test
    void zipSegmentStreamClosesZipFileAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                new Entry("gc.log.0", "first line\nsecond line\n")));
        Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream();

        assertEquals("first line", stream.iterator().next());
        assertArchiveIsOpen(archive);

        stream.close();

        assertArchiveIsClosed(archive);
    }

    @Test
    void zipSegmentClosesZipFileWhenEntryIsMissing() throws IOException {
        Path archive = createZip("missing-segment.zip", List.of(
                new Entry("gc.log.0", "first line\n")));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "missing.log");

        assertThrows(NullPointerException.class, segment::stream);

        assertArchiveIsClosed(archive);
    }

    @Test
    void closeableLineStreamClosesAllResourcesAndCombinesFailures() {
        IOException firstFailure = new IOException("first");
        IOException secondFailure = new IOException("second");
        Closeable first = () -> { throw firstFailure; };
        Closeable second = () -> { throw secondFailure; };
        Stream<String> stream = CloseableStreams.lines(
                new BufferedReader(new StringReader("line")), first, second);

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, stream::close);

        assertSame(firstFailure, thrown.getCause());
        assertSame(secondFailure, thrown.getCause().getSuppressed()[0]);
    }

    @Test
    void rotatingZipStreamClosesEveryArchiveResourceAfterPartialConsumption() throws IOException {
        Path archive = createRotatingZip();
        Stream<String> stream = new RotatingGCLogFile(archive).stream();

        assertEquals("[1.000s] older", stream.iterator().next());
        assertArchiveIsOpen(archive);

        stream.close();

        assertArchiveIsClosed(archive);
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderAndSentinel() throws IOException {
        Path archive = createRotatingZip();

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[1.000s] older",
                    "[2.000s] older-end",
                    "[3.000s] current",
                    "[4.000s] current-end",
                    GCLogFile.END_OF_DATA_SENTINEL), stream.collect(Collectors.toList()));
        }

        assertArchiveIsClosed(archive);
    }

    private Path createRotatingZip() throws IOException {
        return createZip("rotating.zip", List.of(
                new Entry("gc.log.0", "[1.000s] older\n[2.000s] older-end\n"),
                new Entry("gc.log", "[3.000s] current\n[4.000s] current-end\n")));
    }

    private Path createZip(String fileName, List<Entry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Entry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name()));
                output.write(entry.contents().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private static void assertArchiveIsOpen(Path archive) throws IOException {
        assertTrue(openFileDescriptors(archive) > 0, "Expected an open descriptor for " + archive);
    }

    private static void assertArchiveIsClosed(Path archive) throws IOException {
        assertEquals(0, openFileDescriptors(archive), "Expected no open descriptors for " + archive);
    }

    private static long openFileDescriptors(Path archive) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptors), "File descriptor assertions require /proc/self/fd");
        Path realArchive = archive.toRealPath();
        try (Stream<Path> openDescriptors = Files.list(descriptors)) {
            return openDescriptors.filter(descriptor -> pointsTo(descriptor, realArchive)).count();
        }
    }

    private static boolean pointsTo(Path descriptor, Path archive) {
        try {
            Path target = Files.readSymbolicLink(descriptor);
            return target.isAbsolute() && target.normalize().equals(archive);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }

        private String name() {
            return name;
        }

        private String contents() {
            return contents;
        }
    }
}

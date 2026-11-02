// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipStreamResourceTest {

    private static final String ENTRY = "gc.log";
    private static final String CONTENT = "[1.000s][info][gc] first\n[2.000s][info][gc] last\n";

    @TempDir
    Path directory;

    @Test
    void closesEntryAndArchiveWithoutConsumingLines() throws IOException {
        TrackingZipFile archive = archive();
        try (Stream<String> lines = GCLogFileZipSegment.stream(archive, ENTRY)) {
            assertNotNull(lines);
            assertFalse(archive.closed);
        }
        assertTrue(archive.input.closed);
        assertTrue(archive.closed);
    }

    @Test
    void closesEntryAndArchiveAfterShortCircuit() throws IOException {
        TrackingZipFile archive = archive();
        try (Stream<String> lines = GCLogFileZipSegment.stream(archive, ENTRY)) {
            assertEquals("[1.000s][info][gc] first", lines.findFirst().orElseThrow());
        }
        assertTrue(archive.input.closed);
        assertTrue(archive.closed);
    }

    @Test
    void closesEntryAndArchiveAfterFullConsumption() throws IOException {
        TrackingZipFile archive = archive();
        try (Stream<String> lines = GCLogFileZipSegment.stream(archive, ENTRY)) {
            assertEquals(2, lines.count());
        }
        assertTrue(archive.input.closed);
        assertTrue(archive.closed);
    }

    @Test
    void closesArchiveEvenWhenReaderCloseFails() throws IOException {
        TrackingZipFile archive = archive();
        archive.input.failClose = true;
        Stream<String> lines = GCLogFileZipSegment.stream(archive, ENTRY);
        assertThrows(UncheckedIOException.class, lines::close);
        assertTrue(archive.closed);
    }

    @Test
    void preservesBothCloseFailures() throws IOException {
        TrackingZipFile archive = archive();
        archive.input.failClose = true;
        archive.failClose = true;
        Stream<String> lines = GCLogFileZipSegment.stream(archive, ENTRY);
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(archive.closed);
    }

    @Test
    void reportsArchiveCloseFailure() throws IOException {
        TrackingZipFile archive = archive();
        archive.failClose = true;
        Stream<String> lines = GCLogFileZipSegment.stream(archive, ENTRY);
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertEquals("Archive close failed", failure.getCause().getMessage());
        assertTrue(archive.input.closed);
    }

    @Test
    void closesArchiveIfEntryCannotBeOpened() throws IOException {
        TrackingZipFile archive = archive();
        archive.failOpen = true;
        assertThrows(IOException.class, () -> GCLogFileZipSegment.stream(archive, ENTRY));
        assertTrue(archive.closed);
    }

    @Test
    void preservesOpenFailureWhenArchiveCloseAlsoFails() throws IOException {
        TrackingZipFile archive = archive();
        archive.failOpen = true;
        archive.failClose = true;
        IOException failure = assertThrows(IOException.class,
                () -> GCLogFileZipSegment.stream(archive, ENTRY));
        assertEquals("Entry open failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(archive.closed);
    }

    @Test
    void missingEntryIsAnExplicitFailureAndClosesArchive() throws IOException {
        TrackingZipFile archive = archive();
        assertThrows(IOException.class, () -> GCLogFileZipSegment.stream(archive, "missing.log"));
        assertTrue(archive.closed);
    }

    @Test
    void publicStreamsPreserveContentAndSentinel() throws IOException {
        Path zip = createArchive();
        List<String> expected = List.of("[1.000s][info][gc] first", "[2.000s][info][gc] last",
                GCLogFile.END_OF_DATA_SENTINEL);
        try (Stream<String> lines = new SingleGCLogFile(zip).stream()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new RotatingGCLogFile(zip).stream()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zip, ENTRY);
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
    }

    @Test
    void closesReaderInputAndOnlyClosesOnce() {
        TrackingInputStream input = new TrackingInputStream();
        Stream<String> lines = LogFileStreams.lines(input);
        lines.close();
        lines.close();
        assertEquals(1, input.closeCount);
    }

    @Test
    void zipInputClosesUnderlyingInputOnEarlyClose() throws IOException {
        TrackingInputStream input = new TrackingInputStream(Files.readAllBytes(createArchive()));
        try (Stream<String> lines = LogFileStreams.zipLines(new ZipInputStream(input))) {
            assertEquals("[1.000s][info][gc] first", lines.findFirst().orElseThrow());
        }
        assertTrue(input.closed);
    }

    @Test
    void metadataProbesCloseTheirStreamsAndCacheResults() {
        AtomicInteger closeCount = new AtomicInteger();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(directory.resolve("unused.zip"), ENTRY) {
            @Override
            public Stream<String> stream() {
                return CONTENT.lines().onClose(closeCount::incrementAndGet);
            }
        };
        assertEquals(1.0, segment.getStartTime());
        assertEquals(1, closeCount.get());
        assertEquals(2.0, segment.getEndTime());
        assertEquals(2, closeCount.get());
        segment.getStartTime();
        segment.getEndTime();
        assertEquals(2, closeCount.get());
    }

    @Test
    void malformedEntryClosesInputAndPreservesCloseFailure() {
        TrackingInputStream input = new TrackingInputStream();
        input.failClose = true;
        ZipInputStream zip = new ZipInputStream(input) {
            @Override
            public ZipEntry getNextEntry() throws IOException {
                throw new IOException("Entry header failed");
            }
        };
        IOException failure = assertThrows(IOException.class, () -> LogFileStreams.zipLines(zip));
        assertEquals("Entry header failed", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(input.closed);
    }

    @Test
    void malformedEntryClosesInput() {
        TrackingInputStream input = new TrackingInputStream();
        ZipInputStream zip = new ZipInputStream(input) {
            @Override
            public ZipEntry getNextEntry() throws IOException {
                throw new IOException("Entry header failed");
            }
        };
        assertThrows(IOException.class, () -> LogFileStreams.zipLines(zip));
        assertTrue(input.closed);
    }

    @Test
    void skipsDirectoriesAndHandlesEmptyArchives() throws IOException {
        Path zip = Files.createTempFile(directory, "directories-", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/" + ENTRY));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (Stream<String> lines = LogFileStreams.zipLines(zip)) {
            assertEquals(2, lines.count());
        }
        Path emptyZip = Files.createTempFile(directory, "empty-", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            output.finish();
        }
        try (Stream<String> lines = LogFileStreams.zipLines(emptyZip)) {
            assertEquals(0, lines.count());
        }
    }

    @Test
    void missingArchiveIsAnExplicitFailure() {
        GCLogFileZipSegment segment = new GCLogFileZipSegment(directory.resolve("missing.zip"), ENTRY);
        assertThrows(UncheckedIOException.class, segment::stream);
    }

    private TrackingZipFile archive() throws IOException {
        return new TrackingZipFile(createArchive());
    }

    private Path createArchive() throws IOException {
        Path zip = Files.createTempFile(directory, "gc-", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry(ENTRY));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return zip;
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;
        private boolean failClose;
        private int closeCount;

        TrackingInputStream() {
            this(CONTENT.getBytes(StandardCharsets.UTF_8));
        }

        TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            closeCount++;
            super.close();
            if (failClose)
                throw new IOException("Entry close failed");
        }
    }

    private static class TrackingZipFile extends ZipFile {
        private final TrackingInputStream input = new TrackingInputStream();
        private boolean closed;
        private boolean failOpen;
        private boolean failClose;

        TrackingZipFile(Path path) throws IOException {
            super(path.toFile());
        }

        @Override
        public InputStream getInputStream(ZipEntry entry) throws IOException {
            if (failOpen)
                throw new IOException("Entry open failed");
            return input;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
            if (failClose)
                throw new IOException("Archive close failed");
        }
    }
}

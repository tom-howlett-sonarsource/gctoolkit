// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileZipSegmentTest {

    private static final String ENTRY_NAME = "gc.log";

    @TempDir
    Path directory;

    private Path archive;
    private GCLogFileZipSegment segment;

    @BeforeEach
    void createArchive() throws IOException {
        archive = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(ENTRY_NAME));
            output.write("1.000: first\n2.000: last\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        segment = new GCLogFileZipSegment(archive, ENTRY_NAME);
    }

    @Test
    void readsLinesAndClosesReader() {
        Stream<String> lines = segment.stream();
        Iterator<String> iterator = lines.iterator();
        assertEquals("1.000: first", iterator.next());
        lines.close();
        assertThrows(UncheckedIOException.class, iterator::hasNext);
        lines.close();
    }

    @Test
    void preservesLinesAndTimestamps() {
        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of("1.000: first", "2.000: last"), lines.collect(Collectors.toList()));
        }
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
    }

    @Test
    void closesMetadataStreams() {
        int[] closed = {0};
        GCLogFileZipSegment tracked = new GCLogFileZipSegment(archive, ENTRY_NAME) {
            @Override
            public Stream<String> stream() {
                return Stream.of("1.000: first", "2.000: last").onClose(() -> closed[0]++);
            }
        };
        assertEquals(1.0, tracked.getStartTime());
        assertEquals(2.0, tracked.getEndTime());
        assertEquals(2, closed[0]);
    }

    @Test
    void reportsMissingArchive() throws IOException {
        Files.delete(archive);
        assertThrows(UncheckedIOException.class, segment::stream);
    }

    @Test
    void closesEntryAndArchiveWithoutConsumption() throws IOException {
        try (TrackingZipFile file = new TrackingZipFile()) {
            Stream<String> lines = segment.stream(file);
            lines.close();
            assertTrue(file.entryClosed);
            assertTrue(file.archiveClosed);
            assertThrows(IllegalStateException.class, () -> file.getEntry(ENTRY_NAME));
        }
    }

    @Test
    void closesEntryAndArchiveAfterFullConsumption() throws IOException {
        try (TrackingZipFile file = new TrackingZipFile()) {
            try (Stream<String> lines = segment.stream(file)) {
                assertEquals(2, lines.count());
            }
            assertTrue(file.entryClosed);
            assertTrue(file.archiveClosed);
        }
    }

    @Test
    void closesEntryAndArchiveAfterShortCircuit() throws IOException {
        try (TrackingZipFile file = new TrackingZipFile()) {
            try (Stream<String> lines = segment.stream(file)) {
                assertEquals("1.000: first", lines.findFirst().orElseThrow());
            }
            assertTrue(file.entryClosed);
            assertTrue(file.archiveClosed);
        }
    }

    @Test
    void closesArchiveWhenEntryIsMissing() throws IOException {
        try (TrackingZipFile file = new TrackingZipFile()) {
            GCLogFileZipSegment missing = new GCLogFileZipSegment(archive, "missing");
            assertThrows(IOException.class, () -> missing.stream(file));
            assertTrue(file.archiveClosed);
        }
    }

    @Test
    void closesArchiveWhenOpeningEntryFails() throws IOException {
        try (TrackingZipFile file = new TrackingZipFile()) {
            file.failOnOpen = true;
            assertThrows(IOException.class, () -> segment.stream(file));
            assertTrue(file.archiveClosed);
        }
    }

    @Test
    void closesArchiveEvenWhenReaderCloseFails() throws IOException {
        try (TrackingZipFile file = new TrackingZipFile()) {
            file.failOnEntryClose = true;
            Stream<String> lines = segment.stream(file);
            UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
            assertEquals("entry close", failure.getCause().getMessage());
            assertTrue(file.archiveClosed);
            lines.close();
        }
    }

    @Test
    void reportsArchiveCloseFailure() throws IOException {
        TrackingZipFile file = new TrackingZipFile();
        file.failOnArchiveClose = true;
        Stream<String> lines = segment.stream(file);
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertEquals("archive close", failure.getCause().getMessage());
        assertTrue(file.entryClosed);
        assertTrue(file.archiveClosed);
    }

    @Test
    void preservesBothCloseFailures() throws IOException {
        TrackingZipFile file = new TrackingZipFile();
        file.failOnEntryClose = true;
        file.failOnArchiveClose = true;
        Stream<String> lines = segment.stream(file);
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertEquals("entry close", failure.getCause().getMessage());
        assertEquals(1, failure.getCause().getSuppressed().length);
        assertEquals("archive close", failure.getCause().getSuppressed()[0].getMessage());
        assertTrue(file.archiveClosed);
    }

    @Test
    void preservesSetupFailureWhenArchiveCloseAlsoFails() throws IOException {
        TrackingZipFile file = new TrackingZipFile();
        file.failOnOpen = true;
        file.failOnArchiveClose = true;
        IOException failure = assertThrows(IOException.class, () -> segment.stream(file));
        assertEquals("entry open", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("archive close", failure.getSuppressed()[0].getMessage());
        assertTrue(file.archiveClosed);
    }

    private class TrackingZipFile extends ZipFile {
        private boolean entryClosed;
        private boolean archiveClosed;
        private boolean failOnOpen;
        private boolean failOnEntryClose;
        private boolean failOnArchiveClose;

        TrackingZipFile() throws IOException {
            super(archive.toFile());
        }

        @Override
        public InputStream getInputStream(ZipEntry entry) throws IOException {
            if (failOnOpen) {
                throw new IOException("entry open");
            }
            return new FilterInputStream(super.getInputStream(entry)) {
                @Override
                public void close() throws IOException {
                    entryClosed = true;
                    super.close();
                    if (failOnEntryClose) {
                        throw new IOException("entry close");
                    }
                }
            };
        }

        @Override
        public void close() throws IOException {
            archiveClosed = true;
            super.close();
            if (failOnArchiveClose) {
                throw new IOException("archive close");
            }
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipFileStreamsTest {

    @TempDir
    Path directory;

    @Test
    void firstEntryClosesArchiveWithoutReading() throws IOException {
        TrackingZipInputStream archive = new TrackingZipInputStream(zipBytes());
        try (Stream<String> lines = ZipFileStreams.firstEntry(archive)) {
            assertFalse(archive.closed);
        }
        assertTrue(archive.closed);
    }

    @Test
    void firstEntrySkipsDirectoriesAndClosesAfterShortCircuit() throws IOException {
        TrackingZipInputStream archive = new TrackingZipInputStream(zipBytes());
        try (Stream<String> lines = ZipFileStreams.firstEntry(archive)) {
            assertEquals("first", lines.findFirst().orElseThrow());
        }
        assertTrue(archive.closed);
    }

    @Test
    void firstEntryClosesAfterExhaustion() throws IOException {
        TrackingZipInputStream archive = new TrackingZipInputStream(zipBytes());
        Stream<String> lines = ZipFileStreams.firstEntry(archive);
        try (Stream<String> owned = lines) {
            assertEquals(List.of("first", "second"), owned.collect(Collectors.toList()));
        }
        lines.close();
        assertTrue(archive.closed);
    }

    @Test
    void emptyArchiveCanBeClosed() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
        }
        TrackingZipInputStream archive = new TrackingZipInputStream(bytes.toByteArray());
        try (Stream<String> lines = ZipFileStreams.firstEntry(archive)) {
            assertEquals(0, lines.count());
        }
        assertTrue(archive.closed);
    }

    @Test
    void firstEntryClosesOnSetupFailureAndSuppressesCloseFailure() {
        IOException failure = new IOException("entry failure");
        IOException closeFailure = new IOException("close failure");
        TrackingZipInputStream archive = new TrackingZipInputStream(new byte[0]) {
            @Override
            public ZipEntry getNextEntry() throws IOException {
                throw failure;
            }

            @Override
            public void close() throws IOException {
                super.close();
                throw closeFailure;
            }
        };
        assertSame(failure, assertThrows(IOException.class, () -> ZipFileStreams.firstEntry(archive)));
        assertTrue(archive.closed);
        assertArrayEquals(new Throwable[]{closeFailure}, failure.getSuppressed());
    }

    @Test
    void closeFailureIsReported() throws IOException {
        IOException failure = new IOException("close failure");
        TrackingZipInputStream archive = new TrackingZipInputStream(zipBytes()) {
            @Override
            public void close() throws IOException {
                super.close();
                throw failure;
            }
        };
        Stream<String> lines = ZipFileStreams.firstEntry(archive);
        assertSame(failure, assertThrows(UncheckedIOException.class, lines::close).getCause());
        assertTrue(archive.closed);
    }

    @Test
    void readFailureStillAllowsArchiveCleanup() throws IOException {
        IOException failure = new IOException("read failure");
        TrackingZipInputStream archive = new TrackingZipInputStream(zipBytes());
        try (Stream<String> lines = ZipFileStreams.firstEntry(archive)) {
            archive.readFailure = failure;
            assertSame(failure, assertThrows(UncheckedIOException.class, lines::count).getCause());
        }
        assertTrue(archive.closed);
    }

    @Test
    void namedEntryClosesArchiveWithoutReading() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(writeZip());
        try (Stream<String> lines = ZipFileStreams.entry(archive, "logs/gc.log")) {
            assertFalse(archive.closed);
        }
        assertTrue(archive.closed);
        assertTrue(archive.entryClosed);
        assertThrows(IllegalStateException.class, () -> archive.getEntry("logs/gc.log"));
    }

    @Test
    void namedEntryClosesReaderAndArchiveAfterShortCircuit() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(writeZip());
        try (Stream<String> lines = ZipFileStreams.entry(archive, "logs/gc.log")) {
            assertEquals("first", lines.findFirst().orElseThrow());
        }
        assertTrue(archive.closed);
        assertTrue(archive.entryClosed);
    }

    @Test
    void namedEntryClosesAfterExhaustion() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(writeZip());
        try (Stream<String> lines = ZipFileStreams.entry(archive, "logs/gc.log")) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
        assertTrue(archive.closed);
        assertTrue(archive.entryClosed);
    }

    @Test
    void archiveIsClosedEvenWhenEntryCloseFails() throws IOException {
        IOException failure = new IOException("entry close failure");
        TrackingZipFile archive = new TrackingZipFile(writeZip()) {
            @Override
            public InputStream getInputStream(ZipEntry entry) throws IOException {
                return new FilterInputStream(super.getInputStream(entry)) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        throw failure;
                    }
                };
            }
        };
        Stream<String> lines = ZipFileStreams.entry(archive, "logs/gc.log");
        assertSame(failure, assertThrows(UncheckedIOException.class, lines::close).getCause());
        assertTrue(archive.entryClosed);
        assertTrue(archive.closed);
    }

    @Test
    void entryOpenFailureClosesArchive() throws IOException {
        IOException failure = new IOException("entry open failure");
        TrackingZipFile archive = new TrackingZipFile(writeZip()) {
            @Override
            public InputStream getInputStream(ZipEntry entry) throws IOException {
                throw failure;
            }
        };
        assertSame(failure, assertThrows(IOException.class,
                () -> ZipFileStreams.entry(archive, "logs/gc.log")));
        assertTrue(archive.closed);
    }

    @Test
    void invalidEntryNameClosesArchive() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(writeZip());
        assertThrows(NullPointerException.class, () -> ZipFileStreams.entry(archive, null));
        assertTrue(archive.closed);
    }

    @Test
    void missingEntryClosesArchiveAndReportsError() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(writeZip());
        IOException failure = assertThrows(IOException.class, () -> ZipFileStreams.entry(archive, "missing"));
        assertTrue(failure.getMessage().contains("missing"));
        assertTrue(archive.closed);
    }

    @Test
    void publicLogSourcesPreserveFilteringAndSentinel() throws IOException {
        Path zip = writeZip();
        try (Stream<String> lines = new SingleGCLogFile(zip).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new GCLogFileZipSegment(zip, "logs/gc.log").stream()) {
            assertEquals(List.of("first", "second"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new RotatingGCLogFile(zip).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void segmentFailureIsNotReportedAsAnEmptyLog() throws IOException {
        GCLogFileZipSegment segment = new GCLogFileZipSegment(writeZip(), "missing");
        assertThrows(UncheckedIOException.class, segment::stream);
    }

    @Test
    void publicLogSourcesPropagateCloseToReaders() throws IOException {
        Path zip = writeZip();
        assertReaderClosed(new SingleGCLogFile(zip).stream());
        assertReaderClosed(new GCLogFileZipSegment(zip, "logs/gc.log").stream());
    }

    private void assertReaderClosed(Stream<String> lines) {
        Spliterator<String> source;
        try (Stream<String> owned = lines) {
            source = owned.spliterator();
        }
        assertThrows(UncheckedIOException.class, () -> source.tryAdvance(line -> {
            throw new AssertionError("Closed reader returned a line");
        }));
    }

    @Test
    void metadataClosesItsShortCircuitAndExhaustedStreams() throws IOException {
        byte[] bytes = zipBytes("[1.000s][info][gc] first\n[2.000s][info][gc] second\n");
        TrackingZipInputStream startArchive = new TrackingZipInputStream(bytes);
        TrackingZipInputStream endArchive = new TrackingZipInputStream(bytes);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(directory.resolve("gc.zip"), "logs/gc.log") {
            @Override
            public Stream<String> stream() {
                try {
                    return ZipFileStreams.firstEntry(startArchive.closed ? endArchive : startArchive);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            }
        };
        assertEquals(1.0, segment.getStartTime());
        assertTrue(startArchive.closed);
        assertEquals(2.0, segment.getEndTime());
        assertTrue(endArchive.closed);
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
    }

    private Path writeZip() throws IOException {
        return Files.write(directory.resolve("gc.zip"), zipBytes());
    }

    private byte[] zipBytes() throws IOException {
        return zipBytes("first\nsecond\n");
    }

    private byte[] zipBytes(String contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("logs/gc.log"));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static class TrackingZipInputStream extends ZipInputStream {
        boolean closed;
        IOException readFailure;

        TrackingZipInputStream(byte[] bytes) {
            super(new ByteArrayInputStream(bytes));
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            return super.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static class TrackingZipFile extends ZipFile {
        boolean closed;
        boolean entryClosed;

        TrackingZipFile(Path path) throws IOException {
            super(path.toFile());
        }

        @Override
        public InputStream getInputStream(ZipEntry entry) throws IOException {
            return new FilterInputStream(super.getInputStream(entry)) {
                @Override
                public void close() throws IOException {
                    entryClosed = true;
                    super.close();
                }
            };
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}

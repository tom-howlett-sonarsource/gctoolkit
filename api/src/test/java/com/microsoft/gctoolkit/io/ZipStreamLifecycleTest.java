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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamLifecycleTest {

    private static final String ENTRY = "gc.log";
    private static final String CONTENT = "[1.000s][info][gc] first\n[2.000s][info][gc] second\n";

    @TempDir
    Path directory;

    @Test
    void closingSingleLogClosesReaderBeforeConsumption() throws IOException {
        Stream<String> lines = new SingleGCLogFile(archive()).stream();
        Iterator<String> iterator = lines.iterator();
        lines.close();
        lines.close();
        assertThrows(UncheckedIOException.class, iterator::hasNext);
    }

    @Test
    void closingSegmentClosesReaderBeforeConsumption() throws IOException {
        Stream<String> lines = new GCLogFileZipSegment(archive(), ENTRY).stream();
        Iterator<String> iterator = lines.iterator();
        lines.close();
        assertThrows(UncheckedIOException.class, iterator::hasNext);
    }

    @Test
    void singleLogPreservesContentAndSentinel() throws IOException {
        try (Stream<String> lines = new SingleGCLogFile(archive()).stream()) {
            assertEquals(List.of("[1.000s][info][gc] first", "[2.000s][info][gc] second",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void segmentMetadataClosesBothStreams() {
        AtomicInteger closed = new AtomicInteger();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(directory, ENTRY) {
            @Override
            public Stream<String> stream() {
                return CONTENT.lines().onClose(closed::incrementAndGet);
            }
        };
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
        assertEquals(2, closed.get());
        segment.getStartTime();
        segment.getEndTime();
        assertEquals(2, closed.get());
    }

    @Test
    void missingSegmentIsReported() throws IOException {
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive(), "missing.log");
        assertThrows(UncheckedIOException.class, segment::stream);
    }

    @Test
    void zipInputClosesArchiveAndRawInputForEveryConsumptionMode() throws IOException {
        for (int limit : new int[]{0, 1, Integer.MAX_VALUE}) {
            TrackingInputStream input = new TrackingInputStream(new ByteArrayInputStream(archiveBytes()));
            ZipInputStream archive = new ZipInputStream(input);
            Stream<String> lines = ZipLogFile.stream(archive);
            try (Stream<String> owned = lines) {
                assertEquals(Math.min(limit, 2), owned.limit(limit).count());
            }
            lines.close();
            assertEquals(1, input.closeCount);
            assertThrows(IOException.class, archive::getNextEntry);
        }
    }

    @Test
    void namedEntryClosesReaderEntryAndArchiveForEveryConsumptionMode() throws IOException {
        Path path = archive();
        for (int limit : new int[]{0, 1, Integer.MAX_VALUE}) {
            TrackingZipFile archive = new TrackingZipFile(path);
            Stream<String> lines = ZipLogFile.stream(archive, ENTRY);
            try (Stream<String> owned = lines) {
                assertEquals(Math.min(limit, 2), owned.limit(limit).count());
            }
            lines.close();
            assertEquals(1, archive.closeCount);
            assertTrue(archive.input.closeCount > 0);
            assertThrows(IllegalStateException.class, () -> archive.getEntry(ENTRY));
        }
    }

    @Test
    void rotatingStreamClosesMappedArchives() throws IOException {
        Path path = archive();
        for (int limit : new int[]{0, 1, Integer.MAX_VALUE}) {
            List<TrackingZipFile> archives = new ArrayList<>();
            GCLogFileZipSegment segment = new GCLogFileZipSegment(path, ENTRY) {
                @Override
                public Stream<String> stream() {
                    try {
                        TrackingZipFile archive = new TrackingZipFile(path);
                        archives.add(archive);
                        return ZipLogFile.stream(archive, ENTRY);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                }
            };
            LogFileMetadata metadata = new RotatingLogFileMetadata(path) {
                @Override
                public Stream<LogFileSegment> logFiles() {
                    return Stream.of(segment, segment);
                }
            };
            RotatingGCLogFile log = new RotatingGCLogFile(path) {
                @Override
                public LogFileMetadata getMetaData() {
                    return metadata;
                }
            };
            try (Stream<String> lines = log.stream()) {
                assertEquals(Math.min(limit, 5), lines.limit(limit).count());
            }
            assertEquals(Math.min(limit, 2), archives.size());
            for (TrackingZipFile archive : archives) {
                assertEquals(1, archive.closeCount);
                assertTrue(archive.input.closeCount > 0);
            }
        }
    }

    @Test
    void zipInputSetupFailureClosesRawInputAndPreservesSuppressedFailure() {
        IOException opening = new IOException("entry failure");
        IOException closing = new IOException("close failure");
        TrackingInputStream input = new TrackingInputStream(new ByteArrayInputStream(new byte[0]));
        input.closeFailure = closing;
        ZipInputStream archive = new ZipInputStream(input) {
            @Override
            public ZipEntry getNextEntry() throws IOException {
                throw opening;
            }
        };
        assertSame(opening, assertThrows(IOException.class, () -> ZipLogFile.stream(archive)));
        assertEquals(1, input.closeCount);
        assertEquals(List.of(closing), List.of(opening.getSuppressed()));
    }

    @Test
    void missingEntryClosesArchiveEvenWhenCloseFails() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(archive());
        archive.closeFailure = new IOException("archive close failure");
        IOException failure = assertThrows(IOException.class, () -> ZipLogFile.stream(archive, "missing"));
        assertEquals(1, archive.closeCount);
        assertEquals(List.of(archive.closeFailure), List.of(failure.getSuppressed()));
    }

    @Test
    void entryOpenFailureClosesArchive() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(archive());
        archive.openFailure = new IOException("entry open failure");
        assertSame(archive.openFailure,
                assertThrows(IOException.class, () -> ZipLogFile.stream(archive, ENTRY)));
        assertEquals(1, archive.closeCount);
    }

    @Test
    void runtimeSetupFailureClosesArchive() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(archive());
        assertThrows(NullPointerException.class, () -> ZipLogFile.stream(archive, null));
        assertEquals(1, archive.closeCount);
    }

    @Test
    void readerCloseFailureStillClosesArchiveAndReportsBothFailures() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(archive());
        Stream<String> lines = ZipLogFile.stream(archive, ENTRY);
        archive.input.closeFailure = new IOException("entry close failure");
        archive.closeFailure = new IOException("archive close failure");
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertSame(archive.input.closeFailure, failure.getCause());
        assertEquals(1, archive.closeCount);
        assertEquals(1, failure.getSuppressed().length);
        assertSame(archive.closeFailure, failure.getSuppressed()[0].getCause());
    }

    @Test
    void zipInputCloseFailureIsReported() throws IOException {
        TrackingInputStream input = new TrackingInputStream(new ByteArrayInputStream(archiveBytes()));
        Stream<String> lines = ZipLogFile.stream(new ZipInputStream(input));
        input.closeFailure = new IOException("input close failure");
        assertSame(input.closeFailure, assertThrows(UncheckedIOException.class, lines::close).getCause());
        assertEquals(1, input.closeCount);
    }

    @Test
    void consumptionFailureStillClosesAllResources() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(archive());
        IllegalStateException expected = new IllegalStateException("consumer failure");
        Stream<String> lines = ZipLogFile.stream(archive, ENTRY);
        IllegalStateException actual = assertThrows(IllegalStateException.class,
                () -> consumeWithFailure(lines, expected));
        assertSame(expected, actual);
        assertEquals(1, archive.closeCount);
        assertTrue(archive.input.closeCount > 0);
    }

    @Test
    void readFailureRemainsPrimaryWhenCleanupAlsoFails() throws IOException {
        TrackingZipFile archive = new TrackingZipFile(archive());
        Stream<String> lines = ZipLogFile.stream(archive, ENTRY);
        archive.input.readFailure = new IOException("entry read failure");
        archive.input.closeFailure = new IOException("entry close failure");
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, () -> readAll(lines));
        assertSame(archive.input.readFailure, failure.getCause());
        assertEquals(1, failure.getSuppressed().length);
        assertSame(archive.input.closeFailure, failure.getSuppressed()[0].getCause());
        assertEquals(1, archive.closeCount);
    }

    private void readAll(Stream<String> lines) {
        try (Stream<String> owned = lines) {
            owned.count();
        }
    }

    private void consumeWithFailure(Stream<String> lines, RuntimeException failure) {
        try (Stream<String> owned = lines) {
            owned.forEach(line -> {
                throw failure;
            });
        }
    }

    @Test
    void metadataFailureClosesItsStream() {
        AtomicInteger closed = new AtomicInteger();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(directory, ENTRY) {
            @Override
            public Stream<String> stream() {
                return Stream.<String>generate(() -> {
                    throw new UncheckedIOException(new IOException("read failure"));
                }).onClose(closed::incrementAndGet);
            }
        };
        assertThrows(UncheckedIOException.class, segment::getStartTime);
        assertThrows(UncheckedIOException.class, segment::getEndTime);
        assertEquals(2, closed.get());
    }

    @Test
    void emptyAndDirectoryOnlyArchivesProduceNoLines() throws IOException {
        for (boolean includeDirectory : new boolean[]{false, true}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                if (includeDirectory) {
                    zip.putNextEntry(new ZipEntry("logs/"));
                    zip.closeEntry();
                }
            }
            TrackingInputStream input = new TrackingInputStream(new ByteArrayInputStream(bytes.toByteArray()));
            try (Stream<String> lines = ZipLogFile.stream(new ZipInputStream(input))) {
                assertEquals(0, lines.count());
            }
            assertEquals(1, input.closeCount);
        }
    }

    private Path archive() throws IOException {
        Path archive = directory.resolve("logs.zip");
        Files.write(archive, archiveBytes());
        return archive;
    }

    private byte[] archiveBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(ENTRY));
            zip.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("ignored.log"));
            zip.write("ignored\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static class TrackingInputStream extends FilterInputStream {
        private int closeCount;
        private IOException readFailure;
        private IOException closeFailure;

        TrackingInputStream(InputStream input) {
            super(input);
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
            closeCount++;
            super.close();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static class TrackingZipFile extends ZipFile {
        private int closeCount;
        private TrackingInputStream input;
        private IOException openFailure;
        private IOException closeFailure;

        TrackingZipFile(Path path) throws IOException {
            super(path.toFile());
        }

        @Override
        public InputStream getInputStream(ZipEntry entry) throws IOException {
            if (openFailure != null) {
                throw openFailure;
            }
            input = new TrackingInputStream(super.getInputStream(entry));
            return input;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}

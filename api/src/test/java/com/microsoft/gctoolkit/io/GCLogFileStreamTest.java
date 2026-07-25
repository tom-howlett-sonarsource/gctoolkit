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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveWhenPartiallyConsumed() throws IOException {
        Path zipPath = createZip("single.zip", List.of(new Entry("gc.log", " first \nsecond\n")));
        TrackingSingleGCLogFile logFile = new TrackingSingleGCLogFile(zipPath);

        try (Stream<String> stream = logFile.stream()) {
            assertEquals("first", stream.findFirst().orElseThrow());
        }

        assertTrue(logFile.zipInputStream.closed);
    }

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path zipPath = createZip("single.zip", List.of(new Entry("gc.log", " first \n\nsecond\n")));
        TrackingSingleGCLogFile logFile = new TrackingSingleGCLogFile(zipPath);

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertTrue(logFile.zipInputStream.closed);
    }

    @Test
    void singleZipStreamClosesArchiveWhenReadingEntryFails() throws IOException {
        Path zipPath = createZip("single.zip", List.of(new Entry("gc.log", "first\n")));
        FailingEntrySingleGCLogFile logFile = new FailingEntrySingleGCLogFile(zipPath);

        IOException failure = assertThrows(IOException.class, logFile::stream);

        assertTrue(logFile.zipInputStream.closed);
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void singleZipStreamReportsArchiveCloseFailure() throws IOException {
        Path zipPath = createZip("single.zip", List.of(new Entry("gc.log", "first\n")));
        FailingCloseSingleGCLogFile logFile = new FailingCloseSingleGCLogFile(zipPath);
        Stream<String> stream = logFile.stream();

        assertThrows(UncheckedIOException.class, stream::close);
    }

    @Test
    void singleGZipStreamPreservesLinesAndSentinel() throws IOException {
        Path gzipPath = temporaryDirectory.resolve("single.log.gz");
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(gzipPath))) {
            output.write(" first \nsecond\n".getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(gzipPath).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void zipSegmentStreamClosesArchiveWhenPartiallyConsumed() throws IOException {
        Path zipPath = createZip("segment.zip", List.of(new Entry("gc.log.0", "first\nsecond\n")));
        TrackingZipSegment segment = new TrackingZipSegment(zipPath, "gc.log.0");

        try (Stream<String> stream = segment.stream()) {
            assertEquals("first", stream.findFirst().orElseThrow());
        }

        assertTrue(segment.lastZipFile().closed);
    }

    @Test
    void zipSegmentMetadataScansCloseEachArchive() throws IOException {
        Path zipPath = createZip("segment.zip", List.of(new Entry("gc.log.0", "0.100: first\n0.200: second\n")));
        TrackingZipSegment segment = new TrackingZipSegment(zipPath, "gc.log.0");

        segment.getStartTime();
        segment.getEndTime();

        assertEquals(2, segment.zipFiles.size());
        assertTrue(segment.zipFiles.stream().allMatch(zipFile -> zipFile.closed));
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryLookupFails() throws IOException {
        Path zipPath = createZip("segment.zip", List.of(new Entry("gc.log.0", "first\n")));
        TrackingZipSegment segment = new TrackingZipSegment(zipPath, "missing.log");

        assertThrows(NullPointerException.class, segment::stream);

        assertTrue(segment.lastZipFile().closed);
    }

    @Test
    void zipSegmentReportsAllCloseFailures() throws IOException {
        Path zipPath = createZip("segment.zip", List.of(new Entry("gc.log.0", "first\n")));
        FailingCloseZipSegment segment = new FailingCloseZipSegment(zipPath, "gc.log.0");
        Stream<String> stream = segment.stream();

        UncheckedIOException failure = assertThrows(UncheckedIOException.class, stream::close);

        assertEquals(1, failure.getCause().getSuppressed().length);
    }

    @Test
    void zipSegmentUsesDefaultArchiveOpener() throws IOException {
        Path zipPath = createZip("segment.zip", List.of(new Entry("gc.log.0", "first\n")));

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(zipPath, "gc.log.0").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first"), lines);
    }

    @Test
    void rotatingZipStreamClosesCurrentArchiveWhenPartiallyConsumed() throws IOException {
        Path zipPath = createRotatingZip();
        TrackingZipSegment first = new TrackingZipSegment(zipPath, "gc.log.1");
        TrackingZipSegment second = new TrackingZipSegment(zipPath, "gc.log.0");
        RotatingGCLogFile logFile = rotatingLogFile(zipPath, List.of(first, second));

        try (Stream<String> stream = logFile.stream()) {
            assertEquals("oldest", stream.findFirst().orElseThrow());
        }

        assertTrue(first.lastZipFile().closed);
        assertFalse(second.wasOpened());
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderLinesAndSentinel() throws IOException {
        Path zipPath = createRotatingZip();
        TrackingZipSegment first = new TrackingZipSegment(zipPath, "gc.log.1");
        TrackingZipSegment second = new TrackingZipSegment(zipPath, "gc.log.0");
        RotatingGCLogFile logFile = rotatingLogFile(zipPath, List.of(first, second));

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("oldest", "newest", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertTrue(first.lastZipFile().closed);
        assertTrue(second.lastZipFile().closed);
    }

    private Path createRotatingZip() throws IOException {
        return createZip("rotating.zip", List.of(
                new Entry("gc.log.1", " oldest \n"),
                new Entry("gc.log.0", " newest \n")));
    }

    private Path createZip(String fileName, List<Entry> entries) throws IOException {
        Path zipPath = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Entry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zipPath;
    }

    private RotatingGCLogFile rotatingLogFile(Path path, List<LogFileSegment> segments) throws IOException {
        LogFileMetadata metadata = new TestRotatingMetadata(path, segments);
        return new RotatingGCLogFile(path) {
            @Override
            public LogFileMetadata getMetaData() {
                return metadata;
            }
        };
    }

    private static final class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    private static final class TrackingSingleGCLogFile extends SingleGCLogFile {
        private TrackingZipInputStream zipInputStream;

        private TrackingSingleGCLogFile(Path path) {
            super(path);
        }

        @Override
        ZipInputStream openZipInputStream(Path path) throws IOException {
            zipInputStream = new TrackingZipInputStream(Files.newInputStream(path));
            return zipInputStream;
        }
    }

    private static class TrackingZipInputStream extends ZipInputStream {
        private boolean closed;

        private TrackingZipInputStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingEntrySingleGCLogFile extends SingleGCLogFile {
        private FailingEntryZipInputStream zipInputStream;

        private FailingEntrySingleGCLogFile(Path path) {
            super(path);
        }

        @Override
        ZipInputStream openZipInputStream(Path path) {
            zipInputStream = new FailingEntryZipInputStream();
            return zipInputStream;
        }
    }

    private static final class FailingEntryZipInputStream extends ZipInputStream {
        private boolean closed;

        private FailingEntryZipInputStream() {
            super(new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public ZipEntry getNextEntry() throws IOException {
            throw new IOException("entry failure");
        }

        @Override
        public void close() throws IOException {
            closed = true;
            throw new IOException("close failure");
        }
    }

    private static final class FailingCloseSingleGCLogFile extends SingleGCLogFile {
        private FailingCloseSingleGCLogFile(Path path) {
            super(path);
        }

        @Override
        ZipInputStream openZipInputStream(Path path) throws IOException {
            return new TrackingZipInputStream(Files.newInputStream(path)) {
                @Override
                public void close() throws IOException {
                    super.close();
                    throw new IOException("close failure");
                }
            };
        }
    }

    private static final class TrackingZipSegment extends GCLogFileZipSegment {
        private final List<TrackingZipFile> zipFiles = new ArrayList<>();

        private TrackingZipSegment(Path path, String segmentName) {
            super(path, segmentName);
        }

        @Override
        ZipFile openZipFile() throws IOException {
            TrackingZipFile zipFile = new TrackingZipFile(getPath());
            zipFiles.add(zipFile);
            return zipFile;
        }

        private boolean wasOpened() {
            return !zipFiles.isEmpty();
        }

        private TrackingZipFile lastZipFile() {
            return zipFiles.get(zipFiles.size() - 1);
        }
    }

    private static final class TrackingZipFile extends ZipFile {
        private boolean closed;

        private TrackingZipFile(Path path) throws IOException {
            super(path.toFile());
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingCloseZipSegment extends GCLogFileZipSegment {
        private FailingCloseZipSegment(Path path, String segmentName) {
            super(path, segmentName);
        }

        @Override
        ZipFile openZipFile() throws IOException {
            return new FailingCloseZipFile(getPath());
        }
    }

    private static final class FailingCloseZipFile extends ZipFile {
        private FailingCloseZipFile(Path path) throws IOException {
            super(path.toFile());
        }

        @Override
        public InputStream getInputStream(ZipEntry entry) {
            return new ByteArrayInputStream("first\n".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() throws IOException {
                    throw new IOException("reader close failure");
                }
            };
        }

        @Override
        public void close() throws IOException {
            super.close();
            throw new IOException("archive close failure");
        }
    }

    private static final class TestRotatingMetadata extends LogFileMetadata {
        private final List<LogFileSegment> segments;

        private TestRotatingMetadata(Path path, List<LogFileSegment> segments) throws IOException {
            super(path);
            this.segments = new ArrayList<>(segments);
        }

        @Override
        public Stream<LogFileSegment> logFiles() {
            return segments.stream();
        }

        @Override
        public int getNumberOfFiles() {
            return segments.size();
        }

        @Override
        public boolean isZip() {
            return true;
        }
    }
}

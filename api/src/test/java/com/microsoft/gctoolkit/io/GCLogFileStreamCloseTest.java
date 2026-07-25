package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path zipPath = createZip("single.zip", List.of(
                new Entry("gc.log", " first line \n\nsecond line\n")));
        AtomicBoolean archiveClosed = new AtomicBoolean();
        SingleGCLogFile logFile = new SingleGCLogFile(zipPath,
                ignored -> new TrackingInputStream(Files.newInputStream(zipPath), archiveClosed));

        Stream<String> stream = logFile.stream();
        assertEquals("first line", stream.findFirst().orElseThrow());
        assertFalse(archiveClosed.get());

        stream.close();

        assertTrue(archiveClosed.get());
    }

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path zipPath = createZip("single-complete.zip", List.of(
                new Entry("gc.log", " first line \n\nsecond line\n")));
        SingleGCLogFile logFile = new SingleGCLogFile(zipPath);

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(toList());
        }

        assertEquals(List.of("first line", "second line", logFile.endOfData()), lines);
    }

    @Test
    void singleZipClosesArchiveWhenOpeningAnEntryFails() throws IOException {
        Path zipPath = createZip("single-read-failure.zip", List.of(new Entry("gc.log", "line\n")));
        AtomicBoolean archiveClosed = new AtomicBoolean();
        SingleGCLogFile logFile = new SingleGCLogFile(zipPath,
                ignored -> new FailingInputStream(archiveClosed, true, false));

        assertThrows(IOException.class, logFile::stream);

        assertTrue(archiveClosed.get());
    }

    @Test
    void singleZipReportsArchiveCloseFailure() throws IOException {
        Path zipPath = createZip("single-close-failure.zip", List.of(new Entry("gc.log", "line\n")));
        AtomicBoolean archiveClosed = new AtomicBoolean();
        SingleGCLogFile logFile = new SingleGCLogFile(zipPath,
                ignored -> new FailingInputStream(Files.newInputStream(zipPath), archiveClosed, false, true));

        Stream<String> stream = logFile.stream();
        assertEquals("line", stream.findFirst().orElseThrow());

        assertThrows(UncheckedIOException.class, stream::close);
        assertTrue(archiveClosed.get());
    }

    @Test
    void singleGZipClosesArchiveAfterPartialConsumption() throws IOException {
        Path gzipPath = temporaryDirectory.resolve("single.log.gz");
        try (java.util.zip.GZIPOutputStream output =
                     new java.util.zip.GZIPOutputStream(Files.newOutputStream(gzipPath))) {
            output.write("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
        }
        AtomicBoolean archiveClosed = new AtomicBoolean();
        SingleGCLogFile logFile = new SingleGCLogFile(gzipPath,
                ignored -> new TrackingInputStream(Files.newInputStream(gzipPath), archiveClosed));

        Stream<String> stream = logFile.stream();
        assertEquals("first line", stream.findFirst().orElseThrow());
        stream.close();

        assertTrue(archiveClosed.get());
    }

    @Test
    void zipSegmentStreamClosesZipFileAfterPartialConsumption() throws IOException {
        Path zipPath = createZip("segment.zip", List.of(
                new Entry("gc.log.0", "first line\nsecond line\n")));
        AtomicBoolean archiveClosed = new AtomicBoolean();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipPath, "gc.log.0",
                path -> new TrackingZipFile(path, archiveClosed));

        Stream<String> stream = segment.stream();
        assertEquals("first line", stream.findFirst().orElseThrow());
        assertFalse(archiveClosed.get());

        stream.close();

        assertTrue(archiveClosed.get());
    }

    @Test
    void zipSegmentTimestampProbesCloseTheirArchives() throws IOException {
        Path zipPath = createZip("segment-timestamps.zip", List.of(
                new Entry("gc.log.0", "[0.123s][info][gc] first line\n[0.456s][info][gc] last line\n")));
        List<AtomicBoolean> archiveClosed = new ArrayList<>();
        GCLogFileZipSegment segment = trackingSegment(zipPath, "gc.log.0", archiveClosed);

        segment.getStartTime();
        segment.getEndTime();

        assertEquals(2, archiveClosed.size());
        assertTrue(archiveClosed.stream().allMatch(AtomicBoolean::get));
    }

    @Test
    void zipSegmentDefaultArchiveOpenerPreservesLines() throws IOException {
        Path zipPath = createZip("segment-default.zip", List.of(new Entry("gc.log.0", "line\n")));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipPath, "gc.log.0");

        List<String> lines;
        try (Stream<String> stream = segment.stream()) {
            lines = stream.collect(toList());
        }

        assertEquals(List.of("line"), lines);
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryLookupFails() throws IOException {
        Path zipPath = createZip("segment-missing.zip", List.of(new Entry("gc.log.0", "line\n")));
        AtomicBoolean archiveClosed = new AtomicBoolean();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipPath, "missing",
                path -> new TrackingZipFile(path, archiveClosed));

        assertThrows(NullPointerException.class, segment::stream);

        assertTrue(archiveClosed.get());
    }

    @Test
    void zipSegmentReportsArchiveCloseFailure() throws IOException {
        Path zipPath = createZip("segment-close-failure.zip", List.of(new Entry("gc.log.0", "line\n")));
        AtomicBoolean archiveClosed = new AtomicBoolean();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zipPath, "gc.log.0",
                path -> new TrackingZipFile(path, archiveClosed, true));

        Stream<String> stream = segment.stream();
        assertEquals("line", stream.findFirst().orElseThrow());

        assertThrows(UncheckedIOException.class, stream::close);
        assertTrue(archiveClosed.get());
    }

    @Test
    void rotatingZipStreamPreservesOrderAndSentinelAndClosesArchives() throws IOException {
        Path zipPath = createZip("rotating.zip", List.of(
                new Entry("gc.log.0", " first old line \nsecond old line\n"),
                new Entry("gc.log", "current line\n")));
        List<AtomicBoolean> archiveClosed = new ArrayList<>();
        List<LogFileSegment> segments = List.of(
                trackingSegment(zipPath, "gc.log.0", archiveClosed),
                trackingSegment(zipPath, "gc.log", archiveClosed));
        RotatingGCLogFile logFile = rotatingLogFile(zipPath, segments);

        List<String> lines;
        try (Stream<String> stream = logFile.stream()) {
            lines = stream.collect(toList());
        }

        assertEquals(List.of("first old line", "second old line", "current line", logFile.endOfData()), lines);
        assertEquals(2, archiveClosed.size());
        assertTrue(archiveClosed.stream().allMatch(AtomicBoolean::get));
    }

    @Test
    void rotatingZipStreamClosesCurrentArchiveAfterPartialConsumption() throws IOException {
        Path zipPath = createZip("rotating-partial.zip", List.of(
                new Entry("gc.log.0", "first old line\nsecond old line\n"),
                new Entry("gc.log", "current line\n")));
        List<AtomicBoolean> archiveClosed = new ArrayList<>();
        List<LogFileSegment> segments = List.of(
                trackingSegment(zipPath, "gc.log.0", archiveClosed),
                trackingSegment(zipPath, "gc.log", archiveClosed));
        RotatingGCLogFile logFile = rotatingLogFile(zipPath, segments);

        Stream<String> stream = logFile.stream();
        assertEquals("first old line", stream.findFirst().orElseThrow());
        stream.close();

        assertEquals(1, archiveClosed.size());
        assertTrue(archiveClosed.get(0).get());
    }

    private GCLogFileZipSegment trackingSegment(Path zipPath, String segmentName, List<AtomicBoolean> archiveClosed) {
        return new GCLogFileZipSegment(zipPath, segmentName, path -> {
            AtomicBoolean closed = new AtomicBoolean();
            archiveClosed.add(closed);
            return new TrackingZipFile(path, closed);
        });
    }

    private RotatingGCLogFile rotatingLogFile(Path zipPath, List<LogFileSegment> segments) throws IOException {
        RotatingLogFileMetadata metadata = new RotatingLogFileMetadata(zipPath) {
            @Override
            public Stream<LogFileSegment> logFiles() {
                return segments.stream();
            }

            @Override
            public int getNumberOfFiles() {
                return segments.size();
            }
        };
        return new RotatingGCLogFile(zipPath) {
            @Override
            public LogFileMetadata getMetaData() {
                return metadata;
            }
        };
    }

    private Path createZip(String fileName, List<Entry> entries) throws IOException {
        Path zipPath = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (Entry entry : entries) {
                zipOutput.putNextEntry(new ZipEntry(entry.name));
                zipOutput.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                zipOutput.closeEntry();
            }
        }
        return zipPath;
    }

    private static final class Entry {
        private final String name;
        private final String contents;

        private Entry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    private static final class TrackingInputStream extends FilterInputStream {
        private final AtomicBoolean closed;

        private TrackingInputStream(InputStream inputStream, AtomicBoolean closed) {
            super(inputStream);
            this.closed = closed;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closed.set(true);
            }
        }
    }

    private static final class FailingInputStream extends FilterInputStream {
        private final AtomicBoolean closed;
        private final boolean failRead;
        private final boolean failClose;

        private FailingInputStream(AtomicBoolean closed, boolean failRead, boolean failClose) {
            this(InputStream.nullInputStream(), closed, failRead, failClose);
        }

        private FailingInputStream(InputStream inputStream, AtomicBoolean closed, boolean failRead, boolean failClose) {
            super(inputStream);
            this.closed = closed;
            this.failRead = failRead;
            this.failClose = failClose;
        }

        @Override
        public int read() throws IOException {
            if (failRead) {
                throw new IOException("read failed");
            }
            return super.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (failRead) {
                throw new IOException("read failed");
            }
            return super.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closed.set(true);
            }
            if (failClose) {
                throw new IOException("close failed");
            }
        }
    }

    private static final class TrackingZipFile extends ZipFile {
        private final AtomicBoolean closed;
        private final boolean failClose;

        private TrackingZipFile(Path path, AtomicBoolean closed) throws IOException {
            this(path, closed, false);
        }

        private TrackingZipFile(Path path, AtomicBoolean closed, boolean failClose) throws IOException {
            super(path.toFile());
            this.closed = closed;
            this.failClose = failClose;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                closed.set(true);
            }
            if (failClose) {
                throw new IOException("close failed");
            }
        }
    }
}

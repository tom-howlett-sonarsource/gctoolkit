package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));
        List<CloseTrackingInputStream> openedStreams = new ArrayList<>();
        SingleGCLogFile logFile = new SingleGCLogFile(archive, path -> {
            CloseTrackingInputStream stream = new CloseTrackingInputStream(Files.newInputStream(path));
            openedStreams.add(stream);
            return stream;
        });

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("first", lines.iterator().next());
        }

        assertEquals(1, openedStreams.size());
        assertTrue(openedStreams.get(0).isClosed());
    }

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = temporaryDirectory.resolve("single.gz");
        try (GZIPOutputStream gzipOutput = new GZIPOutputStream(Files.newOutputStream(archive))) {
            gzipOutput.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        List<CloseTrackingInputStream> openedStreams = new ArrayList<>();
        SingleGCLogFile logFile = new SingleGCLogFile(archive, path -> {
            CloseTrackingInputStream stream = new CloseTrackingInputStream(Files.newInputStream(path));
            openedStreams.add(stream);
            return stream;
        });

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("first", lines.iterator().next());
        }

        assertTrue(openedStreams.get(0).isClosed());
    }

    @Test
    void singleZipStreamClosesArchiveWhenReadingEntryFails() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", "first\n"));
        FailingInputStream failingInputStream = new FailingInputStream();
        SingleGCLogFile logFile = new SingleGCLogFile(archive, path -> failingInputStream);

        assertThrows(IOException.class, logFile::stream);

        assertTrue(failingInputStream.isClosed());
    }

    @Test
    void zipSegmentStreamClosesZipFileAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first\nsecond\n"));
        List<ZipFile> openedFiles = new ArrayList<>();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log", path -> {
            ZipFile zipFile = new ZipFile(path.toFile());
            openedFiles.add(zipFile);
            return zipFile;
        });

        try (Stream<String> lines = segment.stream()) {
            assertEquals("first", lines.iterator().next());
        }

        assertEquals(1, openedFiles.size());
        assertClosed(openedFiles.get(0));
    }

    @Test
    void zipSegmentClosesZipFileWhenEntryIsMissing() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first\n"));
        List<ZipFile> openedFiles = new ArrayList<>();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "missing.log", path -> {
            ZipFile zipFile = new ZipFile(path.toFile());
            openedFiles.add(zipFile);
            return zipFile;
        });

        assertThrows(NullPointerException.class, segment::stream);

        assertClosed(openedFiles.get(0));
    }

    @Test
    void zipSegmentReturnsEmptyStreamWhenArchiveCannotOpen() {
        GCLogFileZipSegment segment = new GCLogFileZipSegment(
                temporaryDirectory.resolve("missing.zip"), "gc.log", path -> {
                    throw new IOException("cannot open");
                });

        try (Stream<String> lines = segment.stream()) {
            assertEquals(0, lines.count());
        }
    }

    @Test
    void rotatingZipStreamClosesEveryZipFileAfterPartialConsumption() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", "[2.000s] current\n[3.000s] current-end\n");
        entries.put("gc.log.0", "[0.000s] old\n[1.000s] old-end\n");
        Path archive = createZip("rotating.zip", entries);
        List<ZipFile> openedFiles = new ArrayList<>();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive, path -> {
            ZipFile zipFile = new ZipFile(path.toFile());
            openedFiles.add(zipFile);
            return zipFile;
        });

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("[0.000s] old", lines.iterator().next());
        }

        assertTrue(openedFiles.size() >= 2);
        openedFiles.forEach(GCLogFileStreamTest::assertClosed);
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderAndSentinel() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", "[2.000s] current\n[3.000s] current-end\n");
        entries.put("gc.log.0", "[0.000s] old\n[1.000s] old-end\n");
        Path archive = createZip("rotating.zip", entries);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[0.000s] old",
                            "[1.000s] old-end",
                            "[2.000s] current",
                            "[3.000s] current-end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }

        assertEquals(2, new RotatingLogFileMetadata(archive).getNumberOfFiles());
    }

    @Test
    void closeableLineStreamAttemptsEveryCloseAndSuppressesLaterFailures() {
        IOException readerFailure = new IOException("reader");
        IOException archiveFailure = new IOException("archive");
        BufferedReader reader = new BufferedReader(new Reader() {
            @Override
            public int read(char[] buffer, int offset, int length) {
                return -1;
            }

            @Override
            public void close() throws IOException {
                throw readerFailure;
            }
        });

        UncheckedIOException exception = assertThrows(UncheckedIOException.class,
                () -> CloseableStreams.lines(reader, () -> {
                    throw archiveFailure;
                }).close());

        assertEquals(readerFailure, exception.getCause());
        assertEquals(List.of(archiveFailure), List.of(exception.getCause().getSuppressed()));
    }

    @Test
    void closeAfterFailureHandlesNullAndSuppressesCloseFailure() {
        IOException failure = new IOException("read");
        CloseableStreams.closeAfterFailure(null, failure);
        CloseableStreams.closeAfterFailure(() -> {
            throw new IOException("close");
        }, failure);

        assertEquals(1, failure.getSuppressed().length);
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zipOutput = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zipOutput.putNextEntry(new ZipEntry(entry.getKey()));
                zipOutput.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipOutput.closeEntry();
            }
        }
        return archive;
    }

    private static void assertClosed(ZipFile zipFile) {
        assertThrows(IllegalStateException.class, zipFile::size);
    }

    private static final class CloseTrackingInputStream extends FilterInputStream {

        private boolean closed;

        private CloseTrackingInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        private boolean isClosed() {
            return closed;
        }
    }

    private static final class FailingInputStream extends InputStream {

        private boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException("cannot read");
        }

        @Override
        public void close() {
            closed = true;
        }

        private boolean isClosed() {
            return closed;
        }
    }
}

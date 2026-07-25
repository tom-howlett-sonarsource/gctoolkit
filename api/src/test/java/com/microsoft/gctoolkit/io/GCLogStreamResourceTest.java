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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
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

class GCLogStreamResourceTest {

    private static final Path PROCESS_FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path tempDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip(Map.of("gc.log", " first line \n\nsecond line\n"));
        Stream<String> lines = new SingleGCLogFile(archive).stream();

        assertEquals("first line", lines.findFirst().orElseThrow());
        assertArchiveOpen(archive);

        lines.close();

        assertArchiveClosed(archive);
    }

    @Test
    void singleZipStreamPreservesContentsAndSentinel() throws IOException {
        Path archive = createZip(Map.of("gc.log", " first line \n\nsecond line\n"));

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertArchiveClosed(archive);
    }

    @Test
    void singleGzipStreamClosesArchiveAndPreservesContents() throws IOException {
        Path archive = createGzip(" first line \n\nsecond line\n");

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertArchiveClosed(archive);
    }

    @Test
    void singleMalformedZipClosesArchiveWhenStreamCreationFails() throws IOException {
        Path archive = createZip(Map.of("gc.log", "line\n"));
        byte[] zipBytes = Files.readAllBytes(archive);
        Files.write(archive, java.util.Arrays.copyOf(zipBytes, 35));

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertArchiveClosed(archive);
    }

    @Test
    void singleMalformedGzipClosesArchiveWhenStreamCreationFails() throws IOException {
        Path archive = tempDirectory.resolve("gc-logs.gz");
        Files.write(archive, new byte[]{0x1f, (byte) 0x8b});

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());

        assertArchiveClosed(archive);
    }

    @Test
    void zipSegmentStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip(Map.of("gc.log.0", " first line \n\nsecond line\n"));
        Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream();

        assertEquals(" first line ", lines.findFirst().orElseThrow());
        assertArchiveOpen(archive);

        lines.close();

        assertArchiveClosed(archive);
    }

    @Test
    void zipSegmentStreamPreservesLineContents() throws IOException {
        Path archive = createZip(Map.of("gc.log.0", " first line \n\nsecond line\n"));

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(" first line ", "", "second line"), lines);
        assertArchiveClosed(archive);
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryIsMissing() throws IOException {
        Path archive = createZip(Map.of("gc.log.0", "line\n"));

        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());

        assertArchiveClosed(archive);
    }

    @Test
    void zipSegmentClosesMalformedArchive() throws IOException {
        Path archive = tempDirectory.resolve("gc-logs.zip");
        Files.write(archive, new byte[]{0x50, 0x4b, 0x03, 0x04});
        Logger logger = Logger.getLogger(GCLogFileZipSegment.class.getName());
        Level originalLevel = logger.getLevel();

        try {
            logger.setLevel(Level.OFF);
            try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
                assertEquals(0, stream.count());
            }
        } finally {
            logger.setLevel(originalLevel);
        }

        assertArchiveClosed(archive);
    }

    @Test
    void rotatingZipStreamClosesAllArchivesAfterPartialConsumption() throws IOException {
        Path archive = createRotatingZip();
        Stream<String> lines = new RotatingGCLogFile(archive).stream();

        assertEquals("[0.000s] oldest start", lines.findFirst().orElseThrow());

        lines.close();

        assertArchiveClosed(archive);
    }

    @Test
    void rotatingZipStreamPreservesOrderingAndSentinel() throws IOException {
        Path archive = createRotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.000s] oldest start",
                "[1.000s] oldest end",
                "[2.000s] middle start",
                "[3.000s] middle end",
                "[4.000s] current start",
                "[5.000s] current end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertArchiveClosed(archive);
    }

    @Test
    void resourceBackedStreamClosesEveryResource() {
        TrackingBufferedReader reader = new TrackingBufferedReader();
        TrackingCloseable additionalResource = new TrackingCloseable();

        ResourceBackedStreams.lines(reader, additionalResource).close();

        assertTrue(reader.closed);
        assertTrue(additionalResource.closed);
    }

    @Test
    void resourceBackedStreamPreservesCloseFailures() {
        IOException readerFailure = new IOException("reader");
        IOException additionalFailure = new IOException("additional");
        TrackingBufferedReader reader = new TrackingBufferedReader(readerFailure);
        TrackingCloseable additionalResource = new TrackingCloseable(additionalFailure);

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                () -> ResourceBackedStreams.lines(reader, additionalResource).close());

        assertSame(readerFailure, thrown.getCause());
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(additionalFailure, ((UncheckedIOException) thrown.getSuppressed()[0]).getCause());
    }

    @Test
    void closeAfterFailureAddsSuppressedCloseFailure() {
        IOException failure = new IOException("stream creation");
        IOException closeFailure = new IOException("close");

        ResourceBackedStreams.closeAfterFailure(new TrackingCloseable(closeFailure), failure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(closeFailure, failure.getSuppressed()[0]);
    }

    @Test
    void closeAfterFailureAcceptsMissingResource() {
        IOException failure = new IOException("stream creation");

        ResourceBackedStreams.closeAfterFailure(null, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    private Path createRotatingZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[0.000s] oldest start\n[1.000s] oldest end\n");
        entries.put("gc.log.1", "[2.000s] middle start\n[3.000s] middle end\n");
        entries.put("gc.log", "[4.000s] current start\n[5.000s] current end\n");
        return createZip(entries);
    }

    private Path createZip(Map<String, String> entries) throws IOException {
        Path archive = tempDirectory.resolve("gc-logs.zip");
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private Path createGzip(String contents) throws IOException {
        Path archive = tempDirectory.resolve("gc-logs.gz");
        try (OutputStream output = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }

    private void assertArchiveOpen(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(PROCESS_FILE_DESCRIPTORS));
        assertTrue(openDescriptorCount(archive) > 0, "Expected an open descriptor for " + archive);
    }

    private void assertArchiveClosed(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(PROCESS_FILE_DESCRIPTORS));
        assertEquals(0, openDescriptorCount(archive), "Expected no open descriptors for " + archive);
    }

    private long openDescriptorCount(Path archive) throws IOException {
        String archivePath = archive.toAbsolutePath().toString();
        try (Stream<Path> descriptors = Files.list(PROCESS_FILE_DESCRIPTORS)) {
            return descriptors.filter(descriptor -> pointsToArchive(descriptor, archivePath)).count();
        }
    }

    private boolean pointsToArchive(Path descriptor, String archivePath) {
        try {
            return Files.readSymbolicLink(descriptor).toString().equals(archivePath);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static class TrackingBufferedReader extends BufferedReader {
        private final IOException closeFailure;
        private boolean closed;

        private TrackingBufferedReader() {
            this(null);
        }

        private TrackingBufferedReader(IOException closeFailure) {
            super(new StringReader("line"));
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (closeFailure != null) {
                throw closeFailure;
            }
            super.close();
        }
    }

    private static class TrackingCloseable implements Closeable {
        private final IOException closeFailure;
        private boolean closed;

        private TrackingCloseable() {
            this(null);
        }

        private TrackingCloseable(IOException closeFailure) {
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}

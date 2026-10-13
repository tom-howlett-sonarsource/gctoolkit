package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipArchiveClosureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesContentsOrderingAndSentinel() throws IOException {
        Path archive = archive();
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("[1.000s][info][gc] older", "[2.000s][info][gc] older end",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals(List.of("[1.000s][info][gc] older  ", "", "[2.000s][info][gc] older end"),
                    lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of("[1.000s][info][gc] older", "[2.000s][info][gc] older end",
                    "[3.000s][info][gc] middle", "[4.000s][info][gc] middle end",
                    "[5.000s][info][gc] current", "[6.000s][info][gc] current end",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void closesUnconsumedPartiallyConsumedAndExhaustedStreams() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = archive();
        SingleGCLogFile single = new SingleGCLogFile(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");
        RotatingGCLogFile rotating = new RotatingGCLogFile(archive);
        List<StreamOpener> openers = List.of(single::stream, segment::stream, rotating::stream);
        for (StreamOpener opener : openers) {
            for (int consumed : new int[]{0, 1, 4, Integer.MAX_VALUE}) {
                long baseline = descriptorsFor(archive);
                try (Stream<String> lines = opener.open()) {
                    lines.limit(consumed).count();
                }
                assertEquals(baseline, descriptorsFor(archive), "archive descriptors after consuming " + consumed);
            }
        }
    }

    @Test
    void timestampScansCloseTheirArchives() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
        new RotatingGCLogFile(archive).getOrderedGarbageCollectionLogFiles();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void failedEntryLookupClosesArchive() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void closesArchiveEvenWhenReaderCloseFails() {
        IOException readerFailure = new IOException("reader close");
        IOException archiveFailure = new IOException("archive close");
        AtomicBoolean archiveClosed = new AtomicBoolean();
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw readerFailure;
            }
        };
        Stream<String> lines = ArchiveStreams.lines(reader, () -> {
            archiveClosed.set(true);
            throw archiveFailure;
        });
        UncheckedIOException failure = assertThrows(UncheckedIOException.class, lines::close);
        assertTrue(archiveClosed.get());
        assertEquals(readerFailure, failure.getCause());
        assertEquals(archiveFailure, failure.getCause().getSuppressed()[0]);
        lines.close();
    }

    private Path archive() throws IOException {
        Path archive = temporaryDirectory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            entry(output, "directory/", "");
            entry(output, "gc.log.0", "[1.000s][info][gc] older  \n\n[2.000s][info][gc] older end\n");
            entry(output, "gc.log", "[5.000s][info][gc] current\n[6.000s][info][gc] current end\n");
            entry(output, "gc.log.1", "[3.000s][info][gc] middle\n[4.000s][info][gc] middle end\n");
        }
        return archive;
    }

    private void entry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private long descriptorsFor(Path archive) throws IOException {
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).equals(archive.toRealPath())) {
                        count++;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return count;
    }

    private interface StreamOpener {
        Stream<String> open() throws IOException;
    }
}

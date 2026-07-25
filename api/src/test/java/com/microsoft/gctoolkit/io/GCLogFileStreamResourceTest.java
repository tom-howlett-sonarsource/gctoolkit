package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogFileStreamResourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first line \n\nsecond line\n"));
        long baseline = openArchiveHandles(archive);

        Stream<String> lines = new SingleGCLogFile(archive).stream();
        assertEquals("first line", lines.iterator().next());
        assertTrue(openArchiveHandles(archive) > baseline);

        lines.close();

        assertEquals(baseline, openArchiveHandles(archive));
    }

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path archive = createZip("single-content.zip", Map.of("gc.log", " first line \n\nsecond line\n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first line\nsecond line\n"));
        long baseline = openArchiveHandles(archive);

        Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream();
        assertEquals("first line", lines.iterator().next());
        assertTrue(openArchiveHandles(archive) > baseline);

        lines.close();

        assertEquals(baseline, openArchiveHandles(archive));
    }

    @Test
    void zipSegmentStreamPreservesLines() throws IOException {
        Path archive = createZip("segment-content.zip", Map.of("gc.log", " first line \n\nsecond line\n"));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(List.of(" first line ", "", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipStreamClosesAllArchivesAfterPartialConsumption() throws IOException {
        Path archive = createRotatingZip("rotating.zip");
        long baseline = openArchiveHandles(archive);

        Stream<String> lines = new RotatingGCLogFile(archive).stream();
        assertEquals("1.000: old first", lines.iterator().next());

        lines.close();

        assertEquals(baseline, openArchiveHandles(archive));
    }

    @Test
    void rotatingZipStreamPreservesOrderLinesAndSentinel() throws IOException {
        Path archive = createRotatingZip("rotating-content.zip");

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "1.000: old first",
                            "2.000: old last",
                            "3.000: current first",
                            "4.000: current last",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void resourceStreamsCloseEveryResourceWhenClosingFails() {
        AtomicBoolean secondResourceClosed = new AtomicBoolean();
        Stream<String> lines = ResourceStreams.onClose(Stream.empty(),
                () -> {
                    throw new IOException("first close failed");
                },
                () -> secondResourceClosed.set(true));

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, lines::close);

        assertEquals("first close failed", exception.getCause().getMessage());
        assertTrue(secondResourceClosed.get());
    }

    @Test
    void resourceStreamsSuppressCloseFailuresDuringSetupFailure() {
        IOException setupFailure = new IOException("setup failed");

        ResourceStreams.closeAfterFailure(setupFailure, null, () -> {
            throw new IOException("close failed");
        });

        assertEquals(1, setupFailure.getSuppressed().length);
        assertEquals("close failed", setupFailure.getSuppressed()[0].getMessage());
    }

    @Test
    void resourceStreamsSuppressAdditionalCloseFailures() {
        Stream<String> lines = ResourceStreams.onClose(Stream.empty(), null,
                () -> {
                    throw new IOException("first close failed");
                },
                () -> {
                    throw new IOException("second close failed");
                });

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, lines::close);

        assertEquals(1, exception.getCause().getSuppressed().length);
        assertEquals("second close failed", exception.getCause().getSuppressed()[0].getMessage());
    }

    private Path createRotatingZip(String fileName) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "1.000: old first\n2.000: old last\n");
        entries.put("gc.log.1.current", "3.000: current first\n4.000: current last\n");
        return createZip(fileName, entries);
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long openArchiveHandles(Path archive) throws IOException {
        Path fileDescriptors = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(fileDescriptors), "File descriptor assertions require procfs");
        try (Stream<Path> descriptors = Files.list(fileDescriptors)) {
            return descriptors.filter(descriptor -> referencesArchive(descriptor, archive)).count();
        }
    }

    private boolean referencesArchive(Path descriptor, Path archive) {
        try {
            return Files.isSameFile(descriptor, archive);
        } catch (IOException ignored) {
            return false;
        }
    }
}

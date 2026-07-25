package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ZipGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createArchive("single.zip", Map.of(
                "gc.log", " first line \n\nsecond line\n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.iterator().next());
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createArchive("segment.zip", Map.of(
                "gc.log", "first line\nsecond line\n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("first line", lines.iterator().next());
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void rotatingGCLogFileClosesComposedZipStreamAfterPartialConsumption() throws IOException {
        Path archive = createRotatingArchive();
        long descriptorsBefore = openDescriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s] old first", iterator.next());
        }

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
        assertArchiveCanBeMoved(archive);
    }

    @Test
    void zipStreamsPreserveContentsOrderingAndSentinel() throws IOException {
        Path singleArchive = createArchive("single-contents.zip", Map.of(
                "gc.log", " first line \n\nsecond line\n"));
        try (Stream<String> lines = new SingleGCLogFile(singleArchive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }

        Path rotatingArchive = createRotatingArchive();
        try (Stream<String> lines = new RotatingGCLogFile(rotatingArchive).stream()) {
            assertEquals(List.of(
                    "[1.000s] old first",
                    "[1.500s] old last",
                    "[2.000s] current first",
                    "[2.500s] current last",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }

    @Test
    void closeFailureIsReportedAsUncheckedIOException() {
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        };

        Stream<String> lines = CloseableStreams.lines(reader);

        assertThrows(UncheckedIOException.class, lines::close);
    }

    @Test
    void cleanupFailureIsSuppressedOnOriginalFailure() {
        IOException originalFailure = new IOException("open failed");
        IOException closeFailure = new IOException("close failed");
        Closeable resource = () -> {
            throw closeFailure;
        };

        CloseableStreams.closeAfterFailure(resource, originalFailure);

        assertEquals(1, originalFailure.getSuppressed().length);
        assertSame(closeFailure, originalFailure.getSuppressed()[0]);
    }

    private Path createRotatingArchive() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s] old first\n[1.500s] old last\n");
        entries.put("gc.log", "[2.000s] current first\n[2.500s] current last\n");
        return createArchive("rotating.zip", entries);
    }

    private Path createArchive(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long openDescriptorsFor(Path archive) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) {
            return 0L;
        }
        try (Stream<Path> openDescriptors = Files.list(descriptors)) {
            return openDescriptors.filter(descriptor -> references(descriptor, archive)).count();
        }
    }

    private boolean references(Path descriptor, Path archive) {
        try {
            return Files.isSameFile(descriptor, archive);
        } catch (IOException ignored) {
            return false;
        }
    }

    private void assertArchiveCanBeMoved(Path archive) throws IOException {
        Path moved = archive.resolveSibling(archive.getFileName() + ".moved");
        Files.move(archive, moved);
        Files.move(moved, archive);
    }
}

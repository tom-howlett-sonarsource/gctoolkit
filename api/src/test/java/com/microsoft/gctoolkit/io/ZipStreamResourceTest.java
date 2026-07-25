package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipStreamResourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = createZip("single.zip", Map.of(
                "gc.log", " first line \n\nsecond line\n"));
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertArchiveIsOpen(archive);
        }

        assertArchiveIsClosed(archive);
        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
        assertArchiveIsClosed(archive);
    }

    @Test
    void singleGCLogFileClosesGZipWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = temporaryDirectory.resolve("single.log.gz");
        try (OutputStream output = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(" first line \n\nsecond line\n".getBytes(StandardCharsets.UTF_8));
        }
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertArchiveIsOpen(archive);
        }

        assertArchiveIsClosed(archive);
        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
        assertArchiveIsClosed(archive);
    }

    @Test
    void zipSegmentClosesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = createZip("segment.zip", Map.of(
                "gc.log.0", "1.000: first line\n\n2.000: second line\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");

        try (Stream<String> lines = segment.stream()) {
            assertEquals("1.000: first line", lines.findFirst().orElseThrow());
            assertArchiveIsOpen(archive);
        }

        assertArchiveIsClosed(archive);
        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of("1.000: first line", "", "2.000: second line"), lines.collect(toList()));
        }
        assertArchiveIsClosed(archive);
        assertEquals(1.0d, segment.getStartTime());
        assertEquals(2.0d, segment.getEndTime());
        assertArchiveIsClosed(archive);
    }

    @Test
    void rotatingGCLogFileClosesArchiveWhenPartiallyConsumedComposedStreamIsClosed() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "1.000: older first\n2.000: older last\n");
        entries.put("gc.log", "3.000: current first\n4.000: current last\n");
        Path archive = createZip("rotating.zip", entries);
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("1.000: older first", iterator.next());
        }

        assertArchiveIsClosed(archive);
        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of(
                    "1.000: older first",
                    "2.000: older last",
                    "3.000: current first",
                    "4.000: current last",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
        assertArchiveIsClosed(archive);
    }

    @Test
    void closeableStreamClosesEveryResourceWhenOneCloseFails() {
        boolean[] closed = new boolean[2];
        Stream<String> stream = CloseableStream.onClose(Stream.empty(),
                () -> {
                    closed[0] = true;
                    throw new IOException("expected");
                },
                () -> {
                    closed[1] = true;
                    throw new IOException("also expected");
                });

        UncheckedIOException exception = assertThrows(UncheckedIOException.class, stream::close);
        assertTrue(closed[0]);
        assertTrue(closed[1]);
        assertEquals(1, exception.getSuppressed().length);
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
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

    private static void assertArchiveIsOpen(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")), "File descriptor assertions require procfs");
        assertTrue(openDescriptorsFor(archive) > 0, "Expected an open descriptor for " + archive);
    }

    private static void assertArchiveIsClosed(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")), "File descriptor assertions require procfs");
        assertEquals(0, openDescriptorsFor(archive), "Expected no open descriptors for " + archive);
    }

    private static long openDescriptorsFor(Path archive) throws IOException {
        Path normalizedArchive = archive.toAbsolutePath().normalize();
        try (Stream<Path> descriptors = Files.list(Path.of("/proc/self/fd"))) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, normalizedArchive)).count();
        }
    }

    private static boolean pointsTo(Path descriptor, Path archive) {
        try {
            String target = Files.readSymbolicLink(descriptor).toString();
            return target.equals(archive.toString()) || target.equals(archive + " (deleted)");
        } catch (IOException ignored) {
            return false;
        }
    }
}

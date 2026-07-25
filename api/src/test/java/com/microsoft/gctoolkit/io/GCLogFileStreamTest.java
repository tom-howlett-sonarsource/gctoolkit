package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileStreamTest {

    private static final Path FILE_DESCRIPTORS = Paths.get("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void closeFailuresAreReportedAsUncheckedIoExceptions() {
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        };

        Stream<String> stream = CloseableStream.lines(reader);

        assertThrows(UncheckedIOException.class, stream::close);
    }

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip(Map.of("gc.log", " first line \n\nsecond line\n"));
        Stream<String> stream = new SingleGCLogFile(archive).stream();
        Iterator<String> lines = stream.iterator();

        assertEquals("first line", lines.next());
        assertArchiveIsOpen(archive);

        stream.close();

        assertArchiveIsClosed(archive);
        assertThrows(UncheckedIOException.class, lines::next);
    }

    @Test
    void zipSegmentStreamPreservesLineContents() throws IOException {
        Path archive = createZip(Map.of("gc.log.0", " first line \nsecond line\n"));

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(" first line ", "second line"), lines);
        assertArchiveIsClosed(archive);
    }

    @Test
    void singleZipStreamPreservesContentsAndSentinel() throws IOException {
        Path archive = createZip(Map.of("gc.log", " first line \n\nsecond line\n"));

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertArchiveIsClosed(archive);
    }

    @Test
    void zipSegmentStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip(Map.of("gc.log.0", "first line\nsecond line\n"));
        Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream();
        Iterator<String> lines = stream.iterator();

        assertEquals("first line", lines.next());
        assertArchiveIsOpen(archive);

        stream.close();

        assertArchiveIsClosed(archive);
        assertThrows(UncheckedIOException.class, lines::next);
    }

    @Test
    void rotatingZipStreamClosesEveryArchiveHandleAfterPartialConsumption() throws IOException {
        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("gc.log.0", "0.100: oldest\n0.200: older\n");
        segments.put("gc.log", "1.100: newest\n1.200: current\n");
        Path archive = createZip(segments);
        Stream<String> stream = new RotatingGCLogFile(archive).stream();
        Iterator<String> lines = stream.iterator();

        assertEquals("0.100: oldest", lines.next());

        stream.close();

        assertArchiveIsClosed(archive);
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderContentsAndSentinel() throws IOException {
        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("gc.log.0", "0.100: oldest\n0.200: older\n");
        segments.put("gc.log", "1.100: newest\n1.200: current\n");
        Path archive = createZip(segments);

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "0.100: oldest",
                "0.200: older",
                "1.100: newest",
                "1.200: current",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertArchiveIsClosed(archive);
    }

    private Path createZip(Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve("gc-logs.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private static void assertArchiveIsOpen(Path archive) throws IOException {
        if (Files.isDirectory(FILE_DESCRIPTORS)) {
            assertTrue(openArchiveDescriptors(archive) > 0, "Expected an open archive descriptor");
        }
    }

    private static void assertArchiveIsClosed(Path archive) throws IOException {
        if (Files.isDirectory(FILE_DESCRIPTORS)) {
            assertEquals(0, openArchiveDescriptors(archive), "Expected every archive descriptor to be closed");
        }
    }

    private static long openArchiveDescriptors(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        try (Stream<Path> descriptors = Files.list(FILE_DESCRIPTORS)) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, expected)).count();
        }
    }

    private static boolean pointsTo(Path descriptor, Path expected) {
        try {
            return Files.readSymbolicLink(descriptor).equals(expected);
        } catch (IOException ignored) {
            return false;
        }
    }
}

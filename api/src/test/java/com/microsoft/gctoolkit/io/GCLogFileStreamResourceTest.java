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
import java.util.Arrays;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogFileStreamResourceTest {

    private static final Path PROCESS_FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipWhenPartiallyConsumed() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first line \nsecond line\n"));

        assertArchiveClosed(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertArchiveOpen(archive);
            assertEquals("first line", lines.findFirst().orElseThrow());
        }

        assertArchiveClosed(archive);
    }

    @Test
    void zipSegmentClosesArchiveWhenPartiallyConsumed() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first line\nsecond line\n"));

        assertArchiveClosed(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertArchiveOpen(archive);
            assertEquals("first line", lines.findFirst().orElseThrow());
        }

        assertArchiveClosed(archive);
    }

    @Test
    void rotatingGCLogFileClosesAllArchivesWhenPartiallyConsumed() throws IOException {
        Path archive = createRotatingZip();

        assertArchiveClosed(archive);
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s] oldest", iterator.next());
        }

        assertArchiveClosed(archive);
    }

    @Test
    void rotatingGCLogFilePreservesOrderedContentsAndSentinel() throws IOException {
        Path archive = createRotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList(
                "[1.000s] oldest",
                "[1.500s] oldest end",
                "[2.000s] middle",
                "[2.500s] middle end",
                "[3.000s] newest",
                "[3.500s] newest end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void closesRemainingResourcesWhenReaderCloseFails() {
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw new IOException("reader close failed");
            }
        };
        boolean[] archiveClosed = { false };

        Stream<String> lines = CloseableStreams.lines(reader, () -> archiveClosed[0] = true);

        assertThrows(UncheckedIOException.class, lines::close);
        assertTrue(archiveClosed[0]);
    }

    private Path createRotatingZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.1", "[1.000s] oldest\n[1.500s] oldest end\n");
        entries.put("gc.log.0", "\n[2.000s] middle\n[2.500s] middle end\n");
        entries.put("gc.log", "[3.000s] newest\n[3.500s] newest end\n");
        return createZip("rotating.zip", entries);
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private void assertArchiveOpen(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(PROCESS_FILE_DESCRIPTORS));
        assertTrue(countOpenDescriptors(archive) > 0, "expected archive to have an open file descriptor");
    }

    private void assertArchiveClosed(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(PROCESS_FILE_DESCRIPTORS));
        assertEquals(0, countOpenDescriptors(archive), "expected archive file descriptors to be closed");
    }

    private long countOpenDescriptors(Path archive) throws IOException {
        String archivePath = archive.toRealPath().toString();
        try (Stream<Path> descriptors = Files.list(PROCESS_FILE_DESCRIPTORS)) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, archivePath)).count();
        }
    }

    private boolean pointsTo(Path descriptor, String archivePath) {
        try {
            return Files.readSymbolicLink(descriptor).toString().startsWith(archivePath);
        } catch (IOException ignored) {
            return false;
        }
    }
}

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogFileStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void closeableLineStreamReportsCloseFailures() {
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        };

        Stream<String> stream = CloseableLineStream.lines(reader);

        assertThrows(UncheckedIOException.class, stream::close);
    }

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", "  first  \n\nsecond\n"));
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        Stream<String> stream = logFile.stream();
        Iterator<String> lines = stream.iterator();
        assertEquals("first", lines.next());
        assertArchiveOpenWhenObservable(archive);

        stream.close();

        assertArchiveReleased(archive);
        try (Stream<String> completeStream = logFile.stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    completeStream.collect(toList()));
        }
        assertArchiveReleased(archive);
    }

    @Test
    void zipSegmentStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "  first  \nsecond\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        Stream<String> stream = segment.stream();
        Iterator<String> lines = stream.iterator();
        assertEquals("  first  ", lines.next());
        assertArchiveOpenWhenObservable(archive);

        stream.close();

        assertArchiveReleased(archive);
        try (Stream<String> completeStream = segment.stream()) {
            assertEquals(List.of("  first  ", "second"), completeStream.collect(toList()));
        }
        assertArchiveReleased(archive);
    }

    @Test
    void rotatingZipStreamClosesEveryArchiveResourceAndPreservesOutput() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s] older start \n[1.500s] older end \n\n");
        entries.put("gc.log", "[2.000s] current start \n[2.500s] current end \n");
        Path archive = createZip("rotating.zip", entries);
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        Stream<String> partialStream = logFile.stream();
        assertEquals("[1.000s] older start", partialStream.iterator().next());
        partialStream.close();
        assertArchiveReleased(archive);

        try (Stream<String> completeStream = logFile.stream()) {
            assertEquals(List.of(
                    "[1.000s] older start",
                    "[1.500s] older end",
                    "[2.000s] current start",
                    "[2.500s] current end",
                    GCLogFile.END_OF_DATA_SENTINEL), completeStream.collect(toList()));
        }
        assertArchiveReleased(archive);
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

    private long openDescriptorCount(Path archive) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        Path realArchive = archive.toRealPath();
        try (Stream<Path> descriptorStream = Files.list(descriptors)) {
            return descriptorStream.filter(descriptor -> references(descriptor, realArchive)).count();
        }
    }

    private void assertArchiveOpenWhenObservable(Path archive) throws IOException {
        if (Files.isDirectory(Path.of("/proc/self/fd"))) {
            assertTrue(openDescriptorCount(archive) > 0);
        }
    }

    private void assertArchiveReleased(Path archive) throws IOException {
        if (Files.isDirectory(Path.of("/proc/self/fd"))) {
            assertEquals(0, openDescriptorCount(archive));
            return;
        }
        Path movedArchive = archive.resolveSibling(archive.getFileName() + ".moved");
        assertDoesNotThrow(() -> {
            Files.move(archive, movedArchive);
            Files.move(movedArchive, archive);
        });
    }

    private boolean references(Path descriptor, Path archive) {
        try {
            return Path.of(Files.readSymbolicLink(descriptor).toString()).equals(archive);
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }
}

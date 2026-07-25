package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogStreamResourceTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first \nsecond\n"));

        assertPartiallyConsumedStreamClosesArchive(
                () -> new SingleGCLogFile(archive).stream(), archive, "first", true);
    }

    @Test
    void singleGCLogFilePreservesLinesAndSentinel() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));

        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGCLogFileClosesGZipAfterPartialConsumption() throws IOException {
        Path archive = createGZip("single.gz", "first\nsecond\n");

        assertPartiallyConsumedStreamClosesArchive(
                () -> new SingleGCLogFile(archive).stream(), archive, "first", true);
    }

    @Test
    void singleGCLogFileClosesMalformedZipWhenOpeningFails() throws IOException {
        Path validArchive = createZip("valid.zip", Map.of("gc.log", "first\n"));
        Path archive = temporaryDirectory.resolve("malformed.zip");
        Files.write(archive, Arrays.copyOf(Files.readAllBytes(validArchive), 30));

        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());
        assertEquals(0, countOpenDescriptors(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first\nsecond\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        assertPartiallyConsumedStreamClosesArchive(segment::stream, archive, "first", true);
    }

    @Test
    void zipSegmentPreservesLineContents() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", " first \n\nsecond\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        try (Stream<String> stream = segment.stream()) {
            assertEquals(List.of(" first ", "", "second"), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryIsMissing() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "missing.log");

        assertThrows(NullPointerException.class, segment::stream);
        assertEquals(0, countOpenDescriptors(archive));
    }

    @Test
    void zipSegmentHandlesMissingArchiveWithoutLeaking() throws IOException {
        Path archive = temporaryDirectory.resolve("missing.zip");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        try (Stream<String> stream = segment.stream()) {
            assertEquals(0, stream.count());
        }
        assertEquals(0, countOpenDescriptors(archive));
    }

    @Test
    void rotatingGCLogFileClosesActiveZipSegmentAfterPartialConsumption() throws IOException {
        Path archive = createRotatingZip();

        assertPartiallyConsumedStreamClosesArchive(
                () -> new RotatingGCLogFile(archive).stream(), archive, "[1.000s] older", false);
    }

    @Test
    void rotatingGCLogFilePreservesSegmentOrderAndSentinel() throws IOException {
        Path archive = createRotatingZip();

        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[1.000s] older",
                    "[2.000s] older end",
                    "[3.000s] current",
                    "[4.000s] current end",
                    GCLogFile.END_OF_DATA_SENTINEL),
                    stream.collect(Collectors.toList()));
        }
    }

    private void assertPartiallyConsumedStreamClosesArchive(
            StreamFactory streamFactory, Path archive, String expectedLine, boolean remainsOpenWhileStreaming)
            throws IOException {
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        assertEquals(0, countOpenDescriptors(archive));

        Stream<String> stream = streamFactory.open();
        try {
            Iterator<String> iterator = stream.iterator();
            assertEquals(expectedLine, iterator.next());
            if (remainsOpenWhileStreaming) {
                assertTrue(countOpenDescriptors(archive) > 0);
            }
        } finally {
            stream.close();
        }

        assertEquals(0, countOpenDescriptors(archive));
    }

    @FunctionalInterface
    private interface StreamFactory {
        Stream<String> open() throws IOException;
    }

    private long countOpenDescriptors(Path archive) throws IOException {
        String archivePath = archive.toAbsolutePath().normalize().toString();
        try (Stream<Path> descriptors = Files.list(FILE_DESCRIPTORS)) {
            return descriptors
                    .map(this::readSymbolicLink)
                    .filter(path -> archivePath.equals(path.toString()))
                    .count();
        }
    }

    private Path readSymbolicLink(Path descriptor) {
        try {
            return Files.readSymbolicLink(descriptor);
        } catch (IOException ignored) {
            return Path.of("");
        }
    }

    private Path createRotatingZip() throws IOException {
        return createZip("rotating.zip", Map.of(
                "gc.log.0", "[1.000s] older\n[2.000s] older end\n",
                "gc.log", "[3.000s] current\n[4.000s] current end\n"));
    }

    private Path createGZip(String fileName, String content) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (OutputStream output = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
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
}

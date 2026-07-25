package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogStreamResourceTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", " first \n\nsecond\n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        Stream<String> lines = new SingleGCLogFile(archive).stream();
        assertEquals("first", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > descriptorsBefore);

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
    }

    @Test
    void singleGCLogFilePreservesLinesAndSentinel() throws IOException {
        Path archive = createZip("single-content.zip", Map.of("gc.log", " first \n\nsecond\n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }

    @Test
    void singleGCLogFileClosesGzipAfterPartialConsumption() throws IOException {
        Path archive = temporaryDirectory.resolve("single.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(archive))) {
            gzip.write("first\nsecond\n".getBytes(StandardCharsets.UTF_8));
        }
        long descriptorsBefore = openDescriptorsFor(archive);

        Stream<String> lines = new SingleGCLogFile(archive).stream();
        assertEquals("first", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > descriptorsBefore);

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
    }

    @Test
    void singleGCLogFileClosesMalformedZipDuringStreamCreation() throws IOException {
        Path archive = temporaryDirectory.resolve("malformed.zip");
        byte[] localHeader = new byte[31];
        localHeader[0] = 0x50;
        localHeader[1] = 0x4b;
        localHeader[2] = 0x03;
        localHeader[3] = 0x04;
        localHeader[4] = 20;
        localHeader[7] = 0x08;
        localHeader[26] = 1;
        localHeader[30] = (byte) 0xff;
        Files.write(archive, localHeader);
        long descriptorsBefore = openDescriptorsFor(archive);

        assertThrows(IllegalArgumentException.class, () -> new SingleGCLogFile(archive).stream());

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first\nsecond\n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream();
        assertEquals("first", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > descriptorsBefore);

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryCannotBeOpened() throws IOException {
        Path archive = createZip("missing-segment.zip", Map.of("gc.log", "first\n"));
        long descriptorsBefore = openDescriptorsFor(archive);

        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
    }

    @Test
    void rotatingGCLogFileClosesEveryZipSegmentAfterPartialConsumption() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s] old-first\n[2.000s] old-last\n");
        entries.put("gc.log.current", "[3.000s] current-first\n[4.000s] current-last\n");
        Path archive = createZip("rotating.zip", entries);
        long descriptorsBefore = openDescriptorsFor(archive);

        Stream<String> lines = new RotatingGCLogFile(archive).stream();
        assertEquals("[1.000s] old-first", lines.findFirst().orElseThrow());
        assertTrue(openDescriptorsFor(archive) > descriptorsBefore);

        lines.close();

        assertEquals(descriptorsBefore, openDescriptorsFor(archive));
    }

    @Test
    void rotatingGCLogFilePreservesSegmentOrderAndSentinel() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "[1.000s] old-first\n[2.000s] old-last\n");
        entries.put("gc.log.current", "[3.000s] current-first\n[4.000s] current-last\n");
        Path archive = createZip("rotating-content.zip", entries);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[1.000s] old-first",
                    "[2.000s] old-last",
                    "[3.000s] current-first",
                    "[4.000s] current-last",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }

    @Test
    void rotatingGCLogFileReturnsOnlySentinelForUnsupportedGzip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.gz");
        try (GZIPOutputStream gzip = new GZIPOutputStream(Files.newOutputStream(archive))) {
            gzip.write("first\n".getBytes(StandardCharsets.UTF_8));
        }

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }
    }

    @Test
    void closeResourcesClosesAllResourcesAndSuppressesLaterFailures() {
        RuntimeException firstFailure = new RuntimeException("first");
        IOException secondFailure = new IOException("second");
        AtomicBoolean finalResourceClosed = new AtomicBoolean();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> StreamResources.close(
                () -> { throw firstFailure; },
                () -> { throw secondFailure; },
                () -> finalResourceClosed.set(true)));

        assertSame(firstFailure, thrown);
        assertTrue(finalResourceClosed.get());
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(secondFailure, thrown.getSuppressed()[0].getCause());
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

    private long openDescriptorsFor(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS), "File descriptor assertions require /proc/self/fd");
        Path target = archive.toRealPath();
        try (Stream<Path> descriptors = Files.list(FILE_DESCRIPTORS)) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, target)).count();
        }
    }

    private boolean pointsTo(Path descriptor, Path target) {
        try {
            Path linkedPath = Files.readSymbolicLink(descriptor);
            return linkedPath.isAbsolute() && linkedPath.normalize().equals(target);
        } catch (IOException ignored) {
            return false;
        }
    }
}

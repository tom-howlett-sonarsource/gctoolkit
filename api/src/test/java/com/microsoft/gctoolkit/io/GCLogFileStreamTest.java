package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogFileStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                entry("gc.log", "first\nsecond\n")));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(openFileDescriptors(archive) > 0);
        }

        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void singleZipStreamPreservesLinesAndSentinel() throws IOException {
        Path archive = createZip("single.zip", List.of(
                entry("gc.log", " first \n\nsecond\n")));

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void zipSegmentStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                entry("gc.log.0", "first\nsecond\n")));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(openFileDescriptors(archive) > 0);
        }

        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void zipSegmentStreamPreservesLines() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                entry("gc.log.0", " first \n\nsecond\n")));

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(" first ", "", "second"), lines);
    }

    @Test
    void rotatingZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.000s] old", lines.findFirst().orElseThrow());
        }

        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void rotatingZipStreamPreservesSegmentOrderAndSentinel() throws IOException {
        Path archive = rotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.000s] old",
                "[1.000s] old-end",
                "[2.000s] current",
                "[3.000s] current-end",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path rotatingZip() throws IOException {
        return createZip("rotating.zip", List.of(
                entry("gc.log", "[2.000s] current\n[3.000s] current-end\n"),
                entry("gc.log.0", "[0.000s] old\n[1.000s] old-end\n")));
    }

    private Path createZip(String fileName, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name));
                zip.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private static ArchiveEntry entry(String name, String contents) {
        return new ArchiveEntry(name, contents);
    }

    private static long openFileDescriptors(Path path) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorDirectory), "File descriptor inspection requires procfs");
        Path target = path.toRealPath();
        try (Stream<Path> descriptors = Files.list(descriptorDirectory)) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, target)).count();
        }
    }

    private static boolean pointsTo(Path descriptor, Path target) {
        try {
            return Files.readSymbolicLink(descriptor).equals(target);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

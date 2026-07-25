package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Assumptions;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GCLogStreamResourceTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                entry("gc.log", " first line \n\nsecond line\n")));

        SingleGCLogFile logFile = new SingleGCLogFile(archive);
        assertPartialStreamCloseReleasesArchive(logFile::stream, archive, "first line");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGCLogFileClosesMalformedZipWhenStreamCreationFails() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        Path archive = temporaryDirectory.resolve("malformed.zip");
        byte[] malformedHeader = new byte[30];
        malformedHeader[0] = 0x50;
        malformedHeader[1] = 0x4b;
        malformedHeader[2] = 0x03;
        malformedHeader[3] = 0x04;
        malformedHeader[26] = 0x01;
        Files.write(archive, malformedHeader);
        long descriptorsBefore = openDescriptorCount(archive);

        SingleGCLogFile logFile = new SingleGCLogFile(archive);
        assertThrows(IOException.class, logFile::stream);

        assertEquals(descriptorsBefore, openDescriptorCount(archive));
    }

    @Test
    void singleGCLogFileReportsReaderCloseFailure() {
        BufferedReader reader = new BufferedReader(new StringReader("line")) {
            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        };

        Stream<String> lines = SingleGCLogFile.lines(reader);

        assertThrows(UncheckedIOException.class, lines::close);
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                entry("gc.log.0", "first line\nsecond line\n")));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");

        assertPartialStreamCloseReleasesArchive(segment::stream, archive, "first line");

        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of("first line", "second line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryIsMissing() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        Path archive = createZip("missing-segment.zip", List.of(
                entry("gc.log.0", "first line\n")));
        long descriptorsBefore = openDescriptorCount(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.1").stream()) {
            assertEquals(0, lines.count());
        }

        assertEquals(descriptorsBefore, openDescriptorCount(archive));
    }

    @Test
    void rotatingGCLogFileClosesEveryArchiveResourceAfterPartialConsumption() throws IOException {
        Path archive = createZip("rotating.zip", List.of(
                entry("gc.log.0", "[1.000s][info][gc] old first\n[2.000s][info][gc] old last\n"),
                entry("gc.log", "[3.000s][info][gc] current first\n[4.000s][info][gc] current last\n")));

        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        assertPartialStreamCloseReleasesArchive(logFile::stream, archive,
                "[1.000s][info][gc] old first");

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[1.000s][info][gc] old first",
                    "[2.000s][info][gc] old last",
                    "[3.000s][info][gc] current first",
                    "[4.000s][info][gc] current last",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    private void assertPartialStreamCloseReleasesArchive(StreamSource source, Path archive,
                                                          String expectedFirstLine) throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(FILE_DESCRIPTORS));
        long descriptorsBefore = openDescriptorCount(archive);

        try (Stream<String> lines = source.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals(expectedFirstLine, iterator.next());
        }

        assertEquals(descriptorsBefore, openDescriptorCount(archive));
    }

    private Path createZip(String filename, List<ArchiveEntry> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(filename);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (ArchiveEntry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long openDescriptorCount(Path archive) throws IOException {
        String archivePath = archive.toRealPath().toString();
        try (Stream<Path> descriptors = Files.list(FILE_DESCRIPTORS)) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, archivePath)).count();
        }
    }

    private boolean pointsTo(Path descriptor, String archivePath) {
        try {
            return Files.readSymbolicLink(descriptor).toString().equals(archivePath);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static ArchiveEntry entry(String name, String contents) {
        return new ArchiveEntry(name, contents);
    }

    private static final class ArchiveEntry {
        private final String name;
        private final String contents;

        private ArchiveEntry(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }

    @FunctionalInterface
    private interface StreamSource {
        Stream<String> stream() throws IOException;
    }
}

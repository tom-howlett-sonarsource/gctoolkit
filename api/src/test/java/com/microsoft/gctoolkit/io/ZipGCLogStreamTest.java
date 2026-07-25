package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipGCLogStreamTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path tempDirectory;

    @Test
    void singleGCLogFileClosesZipAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", Map.of("gc.log", "first line\nsecond line\n"));
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("first line", lines.iterator().next());
            assertTrue(openDescriptorCount(archive) > 0);
        }

        assertEquals(0, openDescriptorCount(archive));
    }

    @Test
    void singleGCLogFileClosesGZipAfterPartialConsumption() throws IOException {
        Path archive = tempDirectory.resolve("single.gz");
        try (OutputStream output = Files.newOutputStream(archive);
             GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write("first line\nsecond line\n".getBytes(StandardCharsets.UTF_8));
        }
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("first line", lines.iterator().next());
            assertTrue(openDescriptorCount(archive) > 0);
        }

        assertEquals(0, openDescriptorCount(archive));
    }

    @Test
    void singleGCLogFileClosesMalformedZip() throws IOException {
        Path archive = tempDirectory.resolve("malformed.zip");
        Files.write(archive, new byte[]{
                0x50, 0x4b, 0x03, 0x04,
                0x14, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, 0x00,
                (byte) 0xff, (byte) 0xff, 'a'
        });
        SingleGCLogFile logFile = new SingleGCLogFile(archive);

        assertThrows(IOException.class, logFile::stream);

        assertEquals(0, openDescriptorCount(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", Map.of("gc.log", "first line\nsecond line\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");

        try (Stream<String> lines = segment.stream()) {
            assertEquals("first line", lines.iterator().next());
            assertTrue(openDescriptorCount(archive) > 0);
        }

        assertEquals(0, openDescriptorCount(archive));
    }

    @Test
    void zipSegmentClosesArchiveWhenEntryIsMissing() throws IOException {
        Path archive = createZip("missing-entry.zip", Map.of("gc.log", "first line\n"));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "missing.log");

        assertThrows(NullPointerException.class, segment::stream);

        assertEquals(0, openDescriptorCount(archive));
    }

    @Test
    void rotatingGCLogFilePreservesOrderAndClosesPartiallyConsumedZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log.0", "1.000: old first\n1.500: old last\n");
        entries.put("gc.log", "2.000: current first\n2.500: current last\n");
        Path archive = createZip("rotating.zip", entries);
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of(
                            "1.000: old first",
                            "1.500: old last",
                            "2.000: current first",
                            "2.500: current last",
                            logFile.endOfData()),
                    lines.collect(java.util.stream.Collectors.toList()));
        }
        assertEquals(0, openDescriptorCount(archive));

        try (Stream<String> lines = logFile.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("1.000: old first", iterator.next());
        }

        assertEquals(0, openDescriptorCount(archive));
    }

    private Path createZip(String fileName, Map<String, String> entries) throws IOException {
        Path archive = tempDirectory.resolve(fileName);
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

    private long openDescriptorCount(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS), "File descriptor checks require /proc/self/fd");
        Path realArchive = archive.toRealPath();
        try (Stream<Path> descriptors = Files.list(FILE_DESCRIPTORS)) {
            return descriptors.filter(descriptor -> pointsTo(descriptor, realArchive)).count();
        }
    }

    private boolean pointsTo(Path descriptor, Path archive) {
        try {
            return Files.readSymbolicLink(descriptor).equals(archive);
        } catch (IOException ignored) {
            return false;
        }
    }
}

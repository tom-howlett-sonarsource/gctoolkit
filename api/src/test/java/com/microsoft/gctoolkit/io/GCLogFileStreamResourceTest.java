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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GCLogFileStreamResourceTest {

    private static final Path FILE_DESCRIPTORS = Path.of("/proc/self/fd");

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("single.zip", List.of(
                new ZipContent("gc.log", "first line\nsecond line\n")
        ));

        assertEquals(0, openFileDescriptors(archive));
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.findFirst().orElseThrow());
            assertTrue(openFileDescriptors(archive) > 0);
        }
        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void zipSegmentStreamClosesArchiveAfterPartialConsumption() throws IOException {
        Path archive = createZip("segment.zip", List.of(
                new ZipContent("gc.log.0", "  first line  \nsecond line\n")
        ));

        assertEquals(0, openFileDescriptors(archive));
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");
        try (Stream<String> lines = segment.stream()) {
            assertEquals("  first line  ", lines.findFirst().orElseThrow());
            assertTrue(openFileDescriptors(archive) > 0);
        }
        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void rotatingZipStreamClosesAllArchivesAfterPartialConsumption() throws IOException {
        Path archive = createRotatingZip();

        assertEquals(0, openFileDescriptors(archive));
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("1.000: [GC old segment]", iterator.next());
        }
        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void rotatingZipStreamPreservesOrderingContentsAndSentinel() throws IOException {
        Path archive = createRotatingZip();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "1.000: [GC old segment]",
                "1.500: [GC old segment end]",
                "2.000: [GC current segment]",
                "2.500: [GC current segment end]",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
        assertEquals(0, openFileDescriptors(archive));
    }

    @Test
    void resourceStreamReportsCloseFailures() {
        BufferedReader reader = new BufferedReader(new StringReader("line"));
        Closeable failingResource = () -> {
            throw new IOException("close failed");
        };
        Stream<String> lines = ResourceStreams.lines(reader, failingResource);

        assertThrows(UncheckedIOException.class, lines::close);
    }

    private Path createRotatingZip() throws IOException {
        return createZip("rotating.zip", List.of(
                new ZipContent("gc.log.0", "1.000: [GC old segment]\n1.500: [GC old segment end]\n"),
                new ZipContent("gc.log.1.current", "2.000: [GC current segment]\n2.500: [GC current segment end]\n")
        ));
    }

    private Path createZip(String fileName, List<ZipContent> contents) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (ZipContent content : contents) {
                zip.putNextEntry(new ZipEntry(content.name));
                zip.write(content.text.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    private long openFileDescriptors(Path archive) throws IOException {
        assumeTrue(Files.isDirectory(FILE_DESCRIPTORS), "/proc/self/fd is required to verify archive closure");
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

    private static final class ZipContent {
        private final String name;
        private final String text;

        private ZipContent(String name, String text) {
            this.name = name;
            this.text = text;
        }
    }
}

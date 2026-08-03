package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogZipStreamResourceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleFileClosesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = createArchive("single.zip", "gc.log", " first \nsecond\n");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(openDescriptorsFor(archive) > 0);
        }

        assertEquals(0, openDescriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveWhenPartiallyConsumedStreamIsClosed() throws IOException {
        Path archive = createArchive("segment.zip", "gc.log", "first\nsecond\n");

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
            assertTrue(openDescriptorsFor(archive) > 0);
        }

        assertEquals(0, openDescriptorsFor(archive));
    }

    @Test
    void rotatingFileClosesActiveArchiveAndPreservesSentinel() throws IOException {
        Path archive = createArchive("rotating.zip", "gc.log", "first\nsecond\n");
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("first", iterator.next());
        }
        assertEquals(0, openDescriptorsFor(archive));

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of("first", "second", logFile.endOfData()), lines.collect(toList()));
        }
        assertEquals(0, openDescriptorsFor(archive));
    }

    private Path createArchive(String archiveName, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(archiveName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private long openDescriptorsFor(Path path) throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) {
            return 0;
        }
        String expected = path.toRealPath().toString();
        try (Stream<Path> entries = Files.list(descriptors)) {
            return entries.filter(entry -> pointsTo(entry, expected)).count();
        }
    }

    private boolean pointsTo(Path descriptor, String expected) {
        try {
            return Files.readSymbolicLink(descriptor).toString().equals(expected);
        } catch (IOException exception) {
            return false;
        }
    }
}

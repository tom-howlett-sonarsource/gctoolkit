package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipStreamClosureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesLinesAndSentinelAndClosesInEveryConsumptionMode() throws Exception {
        Path archive = zip("directory/", "", "gc.log", " first \n\n second \n", "ignored.log", "ignored\n");
        SingleGCLogFile source = new SingleGCLogFile(archive);
        assertClosure(archive, source::stream, List.of("first", "second", source.endOfData()));
    }

    @Test
    void zipSegmentPreservesUntrimmedLinesAndClosesInEveryConsumptionMode() throws Exception {
        Path archive = zip("gc.log", " first \n\n second \n");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertClosure(archive, segment::stream, List.of(" first ", "", " second "));
    }

    @Test
    void rotatingZipPreservesChronologicalOrderAndSentinelAndCloses() throws Exception {
        Path archive = zip("gc.log", "[3.000s][info][gc] current\n[4.000s][info][gc] last\n",
                "gc.log.0", "[1.000s][info][gc] oldest \n\n[2.000s][info][gc] older\n");
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        assertClosure(archive, source::stream, List.of("[1.000s][info][gc] oldest",
                "[2.000s][info][gc] older", "[3.000s][info][gc] current",
                "[4.000s][info][gc] last", source.endOfData()));
    }

    @Test
    void metadataTimestampScansCloseTheirStreams() throws Exception {
        Path archive = zip("gc.log", "[1.000s][info][gc] first\n[2.000s][info][gc] last\n");
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingEntryDoesNotLeakArchive() throws Exception {
        Path archive = zip("gc.log", "line\n");
        long baseline = descriptorsFor(archive);
        assertThrows(RuntimeException.class, () -> new GCLogFileZipSegment(archive, "missing").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void emptyZipStillProvidesSentinelAndCloses() throws Exception {
        Path archive = zip("directory/", "");
        SingleGCLogFile source = new SingleGCLogFile(archive);
        assertClosure(archive, source::stream, List.of(source.endOfData()));
    }

    private void assertClosure(Path archive, StreamFactory factory, List<String> expected) throws Exception {
        long baseline = descriptorsFor(archive);
        for (int consumed = 0; consumed <= expected.size(); consumed++) {
            try (Stream<String> lines = factory.open()) {
                assertEquals(expected.subList(0, consumed), lines.limit(consumed).collect(Collectors.toList()));
            }
            assertEquals(baseline, descriptorsFor(archive), "leaked after consuming " + consumed + " lines");
        }
        Stream<String> unopened = factory.open();
        unopened.close();
        unopened.close();
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = factory.open()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String... entries) throws IOException {
        Path archive = temporaryDirectory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int index = 0; index < entries.length; index += 2) {
                output.putNextEntry(new ZipEntry(entries[index]));
                output.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorDirectory), "descriptor checks require /proc/self/fd");
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(descriptorDirectory)) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).equals(expected)) {
                        count++;
                    }
                } catch (IOException ignored) {
                }
            }
        }
        return count;
    }

    @FunctionalInterface
    private interface StreamFactory {
        Stream<String> open() throws IOException;
    }
}

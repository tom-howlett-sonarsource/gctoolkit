package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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

class ZipStreamClosureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesFirstEntryFilteringAndSentinel() throws Exception {
        Path archive = archive("single.zip", new String[][] {
                {"directory/", ""},
                {"gc.log", "  first  \n\n \t \n second\n"},
                {"ignored.log", "ignored\n"}
        });
        long baseline = descriptorsFor(archive);
        SingleGCLogFile source = new SingleGCLogFile(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of("first", "second", source.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void unconsumedSingleAndSegmentStreamsCloseIdempotently() throws Exception {
        Path archive = archive("unconsumed.zip", new String[][] {{"gc.log", "first\nsecond\n"}});
        long baseline = descriptorsFor(archive);
        Stream<String> single = new SingleGCLogFile(archive).stream();
        assertTrue(descriptorsFor(archive) > baseline);
        single.close();
        single.close();
        assertEquals(baseline, descriptorsFor(archive));
        Stream<String> segment = new GCLogFileZipSegment(archive, "gc.log").stream();
        assertTrue(descriptorsFor(archive) > baseline);
        segment.close();
        segment.close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipClosesArchiveWhenEntrySetupFails() throws Exception {
        Path archive = archive("encrypted.zip", new String[][] {{"gc.log", "first\n"}});
        byte[] bytes = Files.readAllBytes(archive);
        bytes[6] |= 1;
        Files.write(archive, bytes);
        long baseline = descriptorsFor(archive);
        assertThrows(IOException.class, () -> new SingleGCLogFile(archive).stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleAndSegmentStreamsCloseAfterIteratorReadsOnlyOneLine() throws Exception {
        Path archive = archive("partial.zip", new String[][] {{"gc.log", "first\nsecond\nthird\n"}});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals("first", lines.iterator().next());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentPreservesRawLinesAndClosesAfterFullConsumption() throws Exception {
        Path archive = archive("raw.zip", new String[][] {{"gc.log", " first \n\n second\n"}});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(List.of(" first ", "", " second"), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentMetadataScansCloseTheirStreams() throws Exception {
        Path archive = archive("metadata.zip", new String[][] {
                {"gc.log", "[1.000s][info][gc] first\n[2.000s][info][gc] last\n"}
        });
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingSegmentClosesArchiveOnSetupFailure() throws Exception {
        Path archive = archive("missing.zip", new String[][] {{"gc.log", "first\n"}});
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderingAndSentinelAndReleasesAllArchives() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        assertEquals(List.of("gc.log.1", "gc.log"), source.getOrderedGarbageCollectionLogFiles().stream()
                .map(LogFileSegment::getSegmentName).collect(Collectors.toList()));
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of("[1.000s][info][gc] older", "[2.000s][info][gc] older end",
                    "[3.000s][info][gc] current", "[4.000s][info][gc] current end", source.endOfData()),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesAfterPartialConsumptionInEitherSegment() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        for (int consumed : new int[] {0, 1, 3}) {
            try (Stream<String> lines = source.stream()) {
                Iterator<String> iterator = lines.iterator();
                for (int index = 0; index < consumed; index++) {
                    assertTrue(iterator.hasNext());
                    iterator.next();
                }
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    private Path rotatingArchive() throws IOException {
        return archive("rotating.zip", new String[][] {
                {"gc.log", "[3.000s][info][gc] current  \n\n[4.000s][info][gc] current end\n"},
                {"gc.log.1", "[1.000s][info][gc] older\n[2.000s][info][gc] older end\n"}
        });
    }

    private Path archive(String name, String[][] entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String[] entry : entries) {
                output.putNextEntry(new ZipEntry(entry[0]));
                output.write(entry[1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorDirectory), "Archive descriptor checks require /proc/self/fd");
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
}

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
    void singleZipPreservesContentsAndSentinelAndCloses() throws Exception {
        Path archive = archive("single.zip", new String[][] {
                {"directory/", ""}, {"gc.log", "  first  \n\n second \n"}, {"ignored.log", "ignored\n"}
        });
        SingleGCLogFile source = new SingleGCLogFile(archive);
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of("first", "second", source.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = source.stream()) {
            assertEquals("first", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
        source.stream().close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentPreservesRawLinesAndClosesWithoutConsumption() throws Exception {
        Path archive = archive("segment.zip", new String[][] {{"gc.log", "  first  \n\nsecond\n"}});
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = segment.stream()) {
            assertEquals(List.of("  first  ", "", "second"), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
        Stream<String> unopened = segment.stream();
        unopened.close();
        unopened.close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void timestampScansCloseTheirStreams() throws Exception {
        Path archive = rotatingArchive();
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");
        long baseline = descriptorsFor(archive);
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingEntryClosesTheArchiveOnFailure() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderingAndSentinelAndClosesAllArchives() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of("[1.000s][info][gc] first", "[2.000s][info][gc] second",
                    "[3.000s][info][gc] third", "[4.000s][info][gc] fourth", source.endOfData()),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
        for (int iteration = 0; iteration < 5; iteration++) {
            try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
                assertEquals("[1.000s][info][gc] first", lines.findFirst().orElseThrow());
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
        try (Stream<String> lines = source.stream()) {
            assertEquals(3, lines.limit(3).count());
        }
        assertEquals(baseline, descriptorsFor(archive));
        new RotatingGCLogFile(archive).stream().close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive() throws IOException {
        return archive("rotating.zip", new String[][] {
                {"gc.log", "[3.000s][info][gc] third\n[4.000s][info][gc] fourth\n"},
                {"gc.log.0", "[1.000s][info][gc] first\n[2.000s][info][gc] second\n"}
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
        assumeTrue(Files.isDirectory(descriptorDirectory), "Archive descriptor checks require Linux");
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

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
    void singleZipPreservesFirstEntryContentsAndClosesAtEveryConsumptionLevel() throws Exception {
        Path archive = zip("single.zip", new String[][] {
                {"directory/", ""},
                {"gc.log", "  first  \n\n second \n"},
                {"ignored.log", "ignored\n"}
        });
        long baseline = descriptorsFor(archive);
        for (int consumption = 0; consumption < 3; consumption++) {
            try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
                if (consumption == 1) {
                    assertEquals("first", lines.findFirst().orElseThrow());
                } else if (consumption == 2) {
                    assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                            lines.collect(Collectors.toList()));
                }
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void emptySingleZipPreservesSentinelAndCloses() throws Exception {
        Path archive = zip("empty.zip", new String[][] {{"directory/", ""}});
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of(GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentPreservesRawLinesAndClosesAtEveryConsumptionLevel() throws Exception {
        Path archive = zip("segment.zip", new String[][] {{"gc.log", "  first  \n\nsecond\n"}});
        long baseline = descriptorsFor(archive);
        for (int consumption = 0; consumption < 3; consumption++) {
            Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream();
            try (Stream<String> ownedLines = lines) {
                if (consumption == 1) {
                    assertEquals("  first  ", ownedLines.findFirst().orElseThrow());
                } else if (consumption == 2) {
                    assertEquals(List.of("  first  ", "", "second"), ownedLines.collect(Collectors.toList()));
                }
            }
            lines.close();
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void metadataScansCloseTheirStreams() throws Exception {
        Path archive = zip("metadata.zip", new String[][] {{"gc.log", "[1.000s] first\n[2.000s] last\n"}});
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0d, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingEntryDoesNotLeakArchiveOnStreamCreationFailure() throws Exception {
        Path archive = zip("missing.zip", new String[][] {{"gc.log", "first\n"}});
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class, () -> new GCLogFileZipSegment(archive, "missing").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesChronologicalOrderAndClosesAtEveryConsumptionLevel() throws Exception {
        String first = "[1.000s] first";
        String firstEnd = "[1.500s] first end";
        String second = "[2.000s] second";
        String secondEnd = "[2.500s] second end";
        String current = "[3.000s] current";
        String currentEnd = "[3.500s] current end";
        Path archive = zip("rotating.zip", new String[][] {
                {"gc.log", current + "  \n\n" + currentEnd + "\n"},
                {"gc.log.1", second + "\n" + secondEnd + "\n"},
                {"gc.log.0", first + "\n" + firstEnd + "\n"}
        });
        long baseline = descriptorsFor(archive);
        for (int consumption = 0; consumption < 4; consumption++) {
            try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
                if (consumption == 1) {
                    assertEquals(first, lines.findFirst().orElseThrow());
                } else if (consumption == 2) {
                    assertEquals(first, lines.iterator().next());
                } else if (consumption == 3) {
                    assertEquals(List.of(first, firstEnd, second, secondEnd, current, currentEnd,
                                    GCLogFile.END_OF_DATA_SENTINEL),
                            lines.collect(Collectors.toList()));
                }
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    private Path zip(String name, String[][] entries) throws IOException {
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
        Path descriptorsPath = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorsPath), "Archive descriptor checks require procfs");
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(descriptorsPath)) {
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

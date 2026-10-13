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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipStreamClosureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipClosesWithoutConsumptionAndAfterFullConsumption() throws IOException {
        Path archive = zip("single.zip", "directory/", "", "gc.log", "  first  \n\n second \n",
                "ignored.log", "ignored\n");
        SingleGCLogFile source = new SingleGCLogFile(archive);
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(baseline + 1, descriptorsFor(archive));
        }
        assertEquals(baseline, descriptorsFor(archive));
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of("first", "second", source.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void segmentPreservesRawLinesAndClosesForEveryConsumptionMode() throws IOException {
        Path archive = zip("segment.zip", "gc.log", "  first  \n\n second \n");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        long baseline = descriptorsFor(archive);
        for (int consumption = 0; consumption < 3; consumption++) {
            try (Stream<String> lines = segment.stream()) {
                if (consumption == 1) {
                    assertEquals("  first  ", lines.iterator().next());
                } else if (consumption == 2) {
                    assertEquals(List.of("  first  ", "", " second "), lines.collect(Collectors.toList()));
                }
                assertEquals(baseline + 1, descriptorsFor(archive));
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void rotatingZipClosesMetadataAndComposedStreamsAndPreservesOrdering() throws IOException {
        Path archive = zip("rotating.zip", "gc.log", "[3.000s][info][gc] current \n[4.000s][info][gc] last\n",
                "gc.log.0", "[1.000s][info][gc] oldest \n\n[2.000s][info][gc] older\n");
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        for (int consumption = 0; consumption < 4; consumption++) {
            Stream<String> lines = source.stream();
            try (lines) {
                if (consumption == 1) {
                    assertEquals("[1.000s][info][gc] oldest", lines.findFirst().orElseThrow());
                } else if (consumption == 2) {
                    Iterator<String> iterator = lines.iterator();
                    assertEquals("[1.000s][info][gc] oldest", iterator.next());
                    assertEquals("[2.000s][info][gc] older", iterator.next());
                    assertEquals("[3.000s][info][gc] current", iterator.next());
                } else if (consumption == 3) {
                    assertEquals(List.of("[1.000s][info][gc] oldest", "[2.000s][info][gc] older",
                            "[3.000s][info][gc] current", "[4.000s][info][gc] last", source.endOfData()),
                            lines.collect(Collectors.toList()));
                }
            }
            lines.close();
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void metadataScansCloseTheirShortCircuitedAndFullyConsumedStreams() throws IOException {
        Path archive = zip("metadata.zip", "gc.log", "[1.000s][info][gc] first\n[2.000s][info][gc] last\n");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        long baseline = descriptorsFor(archive);
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingEntryClosesArchiveWhenStreamCreationFails() throws IOException {
        Path archive = zip("missing.zip", "gc.log", "first\n");
        long baseline = descriptorsFor(archive);
        assertThrows(RuntimeException.class, () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
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
        assumeTrue(Files.isDirectory(descriptorDirectory), "Archive descriptor checks require Linux procfs");
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

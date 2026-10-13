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
    Path directory;

    @Test
    void singleZipPreservesContentsAndClosesAtEveryConsumptionDepth() throws IOException {
        Path archive = archive("single.zip", "logs/", "", "logs/gc.log", "  first  \n\n second \n",
                "ignored.log", "ignored\n");
        assertLifecycle(new SingleGCLogFile(archive), archive,
                List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL));
    }

    @Test
    void segmentPreservesUntrimmedLinesAndClosesAtEveryConsumptionDepth() throws IOException {
        Path archive = archive("segment.zip", "gc.log", "  first  \n\n second \n");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertLifecycle(segment::stream, archive, List.of("  first  ", "", " second "));
    }

    @Test
    void rotatingZipPreservesOrderingAndClosesAtEveryConsumptionDepth() throws IOException {
        Path archive = archive("rotating.zip", "gc.log",
                "[3.000s][info][gc] current \n\n[4.000s][info][gc] current end\n",
                "gc.log.0", "[1.000s][info][gc] older \n[2.000s][info][gc] older end \n");
        assertLifecycle(new RotatingGCLogFile(archive), archive,
                List.of("[1.000s][info][gc] older", "[2.000s][info][gc] older end",
                        "[3.000s][info][gc] current", "[4.000s][info][gc] current end",
                        GCLogFile.END_OF_DATA_SENTINEL));
    }

    @Test
    void segmentTimestampProbesCloseTheirStreams() throws IOException {
        Path archive = archive("timestamps.zip", "gc.log",
                "[1.000s][info][gc] start\n[2.000s][info][gc] end\n");
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0, segment.getStartTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingZipEntryDoesNotLeakArchiveOnStreamCreationFailure() throws IOException {
        Path archive = archive("missing.zip", "gc.log", "first\n");
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    private void assertLifecycle(GCLogFile source, Path archive, List<String> expected) throws IOException {
        assertLifecycle(source::stream, archive, expected);
    }

    private void assertLifecycle(StreamSource source, Path archive, List<String> expected) throws IOException {
        long baseline = descriptorsFor(archive);
        for (int consumed = 0; consumed <= expected.size(); consumed++) {
            Stream<String> lines = source.stream();
            try (Stream<String> closeable = lines) {
                assertEquals(expected.subList(0, consumed),
                        closeable.limit(consumed).collect(Collectors.toList()));
            }
            lines.close();
            assertEquals(baseline, descriptorsFor(archive), "consumed " + consumed + " lines");
        }
        for (int consumed = 0; consumed <= expected.size(); consumed++) {
            try (Stream<String> lines = source.stream()) {
                Iterator<String> iterator = lines.iterator();
                for (int index = 0; index < consumed; index++) {
                    assertEquals(expected.get(index), iterator.next());
                }
            }
            assertEquals(baseline, descriptorsFor(archive), "iterator consumed " + consumed + " lines");
        }
        try (Stream<String> lines = source.stream()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path archive(String name, String... entries) throws IOException {
        Path archive = directory.resolve(name);
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
        Path descriptorsPath = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorsPath), "descriptor assertions require Linux procfs");
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

    @FunctionalInterface
    private interface StreamSource {
        Stream<String> stream() throws IOException;
    }
}

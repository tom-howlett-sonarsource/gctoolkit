package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipArchiveClosureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void singleArchiveClosesBeforeReadingAfterOneLineAndAfterExhaustion() throws IOException {
        Path archive = zip("single.zip", new String[][]{{"gc.log", "  first  \n\n second \n"}});
        List<String> expected = Arrays.asList("first", "second", GCLogFile.END_OF_DATA_SENTINEL);
        SingleGCLogFile source = new SingleGCLogFile(archive);
        for (int limit : new int[]{0, 1, expected.size()}) {
            long baseline = descriptorsFor(archive);
            try (Stream<String> lines = source.stream()) {
                assertEquals(expected.subList(0, limit), lines.limit(limit).collect(Collectors.toList()));
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void segmentClosesBeforeReadingAfterOneLineAndAfterExhaustion() throws IOException {
        Path archive = zip("segment.zip", new String[][]{{"gc.log", "  first  \n\n second \n"}});
        List<String> expected = Arrays.asList("  first  ", "", " second ");
        GCLogFileZipSegment source = new GCLogFileZipSegment(archive, "gc.log");
        for (int limit : new int[]{0, 1, expected.size()}) {
            long baseline = descriptorsFor(archive);
            try (Stream<String> lines = source.stream()) {
                assertEquals(expected.subList(0, limit), lines.limit(limit).collect(Collectors.toList()));
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void rotatingArchiveClosesIncludingOrderingProbesAndPartialSegments() throws IOException {
        Path archive = zip("rotating.zip", new String[][]{
                {"gc.log", "[3.000s][info][gc] current  \n[4.000s][info][gc] current end\n"},
                {"gc.log.0", "[1.000s][info][gc] old\n\n[2.000s][info][gc] old end\n"}
        });
        List<String> expected = Arrays.asList(
                "[1.000s][info][gc] old", "[2.000s][info][gc] old end",
                "[3.000s][info][gc] current", "[4.000s][info][gc] current end",
                GCLogFile.END_OF_DATA_SENTINEL);
        for (int limit : new int[]{0, 1, 3, expected.size()}) {
            long baseline = descriptorsFor(archive);
            try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
                assertEquals(expected.subList(0, limit), lines.limit(limit).collect(Collectors.toList()));
            }
            assertEquals(baseline, descriptorsFor(archive));
        }
    }

    @Test
    void timestampProbesCloseTheirStreams() throws IOException {
        Path archive = zip("timestamps.zip", new String[][]{
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
    void missingEntryReleasesArchiveWhenStreamCreationFails() throws IOException {
        Path archive = zip("missing.zip", new String[][]{{"gc.log", "first\n"}});
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class,
                () -> new GCLogFileZipSegment(archive, "missing.log").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String[][] entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
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
        assumeTrue(Files.isDirectory(descriptorsPath), "Archive descriptor checks require Linux procfs");
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(descriptorsPath)) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).equals(expected)) {
                        count++;
                    }
                } catch (IOException ignored) {
                    continue;
                }
            }
        }
        return count;
    }
}

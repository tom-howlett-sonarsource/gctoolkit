// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamResourceLifecycleTest {

    private static final String OLDER = "[1.000s][info][gc] older start\n[2.000s][info][gc] older end\n";
    private static final String NEWER = "[5.000s][info][gc] newer start\n[6.000s][info][gc] newer end\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        logFile.getMetaData().logFiles().count(); // force segment discovery and ordering
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = logFile.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertTrue(iterator.hasNext());
            iterator.next();
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesOrderingAndSentinelAndReleasesArchive() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);
        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }
        assertEquals(List.of("[1.000s][info][gc] older start", "[2.000s][info][gc] older end",
                "[5.000s][info][gc] newer start", "[6.000s][info][gc] newer end", GCLogFile.END_OF_DATA_SENTINEL), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipPreservesContentsAndSentinel() throws Exception {
        Path archive = zip("single.zip", Map.of("gc.log", "  first  \n\nsecond\n"));
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL), stream.collect(Collectors.toList()));
        }
    }

    @Test
    void singleGZipClosesFileAfterPartialConsumption() throws Exception {
        Path archive = temporaryDirectory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            output.write(OLDER.getBytes(StandardCharsets.UTF_8));
        }
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("[1.000s][info][gc] older start", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline);
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentTimesDoNotLeakArchive() throws Exception {
        Path archive = zip("segment.zip", Map.of("gc.log", OLDER + NEWER));
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log");
        assertEquals(1.0d, segment.getStartTime());
        assertEquals(6.0d, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentWithMissingEntryIsEmptyAndDoesNotLeak() throws Exception {
        Path archive = zip("segment.zip", Map.of("gc.log", OLDER));
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "missing.log").stream()) {
            assertEquals(0, lines.count());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("gc.log", NEWER);
        entries.put("gc.log.0", OLDER);
        return zip("rotating.zip", entries);
    }

    private Path zip(String name, Map<String, String> entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        String expected = archive.toRealPath().toString();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).toString().replace(" (deleted)", "").equals(expected)) {
                        count++;
                    }
                } catch (IOException ignored) {
                    // A descriptor can disappear while /proc is being traversed.
                }
            }
        }
        return count;
    }
}

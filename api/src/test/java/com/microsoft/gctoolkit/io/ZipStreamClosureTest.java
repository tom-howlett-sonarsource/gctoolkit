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
    Path directory;

    @Test
    void preservesLinesOrderingAndSentinel() throws IOException {
        Path archive = archive();
        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("[3.000s] current", "detail", "[4.000s] last", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log").stream()) {
            assertEquals(List.of("[3.000s] current", "  detail  ", "", "[4.000s] last"), lines.collect(Collectors.toList()));
        }
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of("[1.000s] older", "[2.000s] previous", "[3.000s] current",
                    "detail", "[4.000s] last", GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void closesArchivesBeforeConsumptionAfterPartialConsumptionAndAfterExhaustion() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        for (int source = 0; source < 3; source++) {
            for (int consumption = 0; consumption < 4; consumption++) {
                try (Stream<String> lines = stream(source, archive)) {
                    if (consumption == 1) {
                        lines.findFirst();
                    } else if (consumption == 2) {
                        Iterator<String> iterator = lines.iterator();
                        assertTrue(iterator.hasNext());
                        iterator.next();
                    } else if (consumption == 3) {
                        lines.count();
                    }
                }
                assertEquals(baseline, descriptorsFor(archive),
                        "source=" + source + ", consumption=" + consumption);
            }
        }
    }

    @Test
    void metadataScansCloseTheirArchives() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        GCLogFileZipSegment segment = new GCLogFileZipSegment(archive, "gc.log.0");
        assertEquals(1.0, segment.getStartTime());
        assertEquals(2.0, segment.getEndTime());
        assertEquals(baseline, descriptorsFor(archive));
        assertEquals(2, new RotatingGCLogFile(archive).getOrderedGarbageCollectionLogFiles().size());
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void missingEntryClosesArchiveOnSetupFailure() throws IOException {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        assertThrows(NullPointerException.class, () -> new GCLogFileZipSegment(archive, "missing").stream());
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Stream<String> stream(int source, Path archive) throws IOException {
        switch (source) {
            case 0:
                return new SingleGCLogFile(archive).stream();
            case 1:
                return new GCLogFileZipSegment(archive, "gc.log").stream();
            default:
                return new RotatingGCLogFile(archive).stream();
        }
    }

    private Path archive() throws IOException {
        Path archive = directory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write("[3.000s] current\n  detail  \n\n[4.000s] last\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write("[1.000s] older\n[2.000s] previous\n".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
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

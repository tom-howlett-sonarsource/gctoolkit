package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipStreamCloseTest {
    private static final String FIRST = "[1.000s][info][gc] first";
    private static final String SECOND = "[2.000s][info][gc] second";
    private static final String THIRD = "[3.000s][info][gc] third";
    private static final String FOURTH = "[4.000s][info][gc] fourth";

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesLinesAndSentinelAfterSkippingDirectory() throws IOException {
        Path archive = archive();
        SingleGCLogFile source = new SingleGCLogFile(archive);
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of(THIRD, FOURTH, source.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentPreservesUntrimmedLinesAndClosesAfterFullConsumption() throws IOException {
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals(List.of(FIRST + "  ", "", SECOND), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void unopenedStreamsReleaseArchivesOnClose() throws IOException {
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        new SingleGCLogFile(archive).stream().close();
        new GCLogFileZipSegment(archive, "gc.log.0").stream().close();
        new RotatingGCLogFile(archive).stream().close();
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesAfterPartialConsumptionIncludingMetadataProbes() throws IOException {
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(FIRST, lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesChronologicalOrderAndSingleSentinel() throws IOException {
        Path archive = archive();
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile source = new RotatingGCLogFile(archive);
        try (Stream<String> lines = source.stream()) {
            assertEquals(List.of(FIRST, SECOND, THIRD, FOURTH, source.endOfData()), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path archive() throws IOException {
        Path archive = temporaryDirectory.resolve("logs.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write((THIRD + "\n" + FOURTH + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log.0"));
            output.write((FIRST + "  \n\n" + SECOND + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path descriptorDirectory = Path.of("/proc/self/fd");
        assumeTrue(Files.isDirectory(descriptorDirectory));
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(descriptorDirectory)) {
            for (Path descriptor : descriptors) {
                if (pointsToArchive(descriptor, archive)) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean pointsToArchive(Path descriptor, Path archive) throws IOException {
        try {
            return Files.isSameFile(descriptor, archive);
        } catch (NoSuchFileException ignored) {
            return false;
        }
    }
}

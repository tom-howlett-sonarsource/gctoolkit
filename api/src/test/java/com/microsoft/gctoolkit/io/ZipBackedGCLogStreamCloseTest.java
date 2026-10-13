package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.util.stream.Collectors.toList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipBackedGCLogStreamCloseTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesActiveSegmentWhenPartiallyConsumed() throws Exception {
        Path archive = rotatingArchive();
        RotatingGCLogFile log = new RotatingGCLogFile(archive);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = log.stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[1.000s][info][gc] old", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipRetainsSegmentOrderLinesAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[1.000s][info][gc] old",
                    "[1.500s][info][gc] old continuation",
                    "[2.000s][info][gc] current",
                    "[2.500s][info][gc] current continuation",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(toList()));
        }

        assertEquals(0, descriptorsFor(archive));
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[1.000s][info][gc] old\n[1.500s][info][gc] old continuation\n");
            writeEntry(output, "gc.log", "[2.000s][info][gc] current\n[2.500s][info][gc] current continuation\n");
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) count++;
                } catch (IOException ignored) {
                    // Descriptors can disappear while /proc is traversed.
                }
            }
        }
        return count;
    }
}

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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipStreamClosureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleLogClosesZipAfterFullConsumptionAndPreservesContents() throws Exception {
        Path archive = zip("single.zip",
                "gc.log", "  first line  \n\nsecond line\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
            assertTrue(descriptorsFor(archive) > baseline, "the archive should remain open until stream close");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterPartialConsumption() throws Exception {
        Path archive = zip("segment.zip",
                "gc.log.0", " first line \n\nsecond line\n");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "gc.log.0").stream()) {
            assertEquals(" first line ", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the archive should be open while its stream is open");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogClosesZipAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.010s][info][gc] oldest", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingLogPreservesSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                            "[0.010s][info][gc] oldest",
                            "[0.020s][info][gc] oldest-end",
                            "[0.030s][info][gc] middle",
                            "[0.040s][info][gc] middle-end",
                            "[0.050s][info][gc] current",
                            "[0.060s][info][gc] current-end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingZip() throws IOException {
        return zip("rotating.zip",
                "gc.log.0", "[0.010s][info][gc] oldest\n[0.020s][info][gc] oldest-end\n",
                "gc.log.1", "[0.030s][info][gc] middle\n[0.040s][info][gc] middle-end\n",
                "gc.log", "[0.050s][info][gc] current\n[0.060s][info][gc] current-end\n");
    }

    private Path zip(String name, String... entriesAndContents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (int index = 0; index < entriesAndContents.length; index += 2) {
                output.putNextEntry(new ZipEntry(entriesAndContents[index]));
                output.write(entriesAndContents[index + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return archive;
    }

    private long descriptorsFor(Path archive) throws IOException {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    Path target = Files.readSymbolicLink(descriptor);
                    if (target.toString().replace(" (deleted)", "").equals(expected.toString())) {
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

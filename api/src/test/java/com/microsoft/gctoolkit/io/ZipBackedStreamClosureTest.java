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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipBackedStreamClosureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleFileRetainsContentAndSentinelAndClosesWhenPartiallyConsumed() throws Exception {
        Path archive = zip("single.zip", new String[][] {
                {"gc.log", " first line \n\n second line \n"}
        });
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals("first line", lines.iterator().next());
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentRetainsRawLinesAndClosesWhenPartiallyConsumed() throws Exception {
        Path archive = zip("segment.zip", new String[][] {
                {"segment.log", " first line \n\nsecond line  \n"}
        });
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(" first line ", lines.iterator().next());
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(List.of(" first line ", "", "second line  "), lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingFileRetainsSegmentOrderAndSentinelAndClosesWhenPartiallyConsumed() throws Exception {
        Path archive = zip("rotating.zip", new String[][] {
                {"gc.log", "[3.000s][info][gc] current first\n[4.000s][info][gc] current second\n"},
                {"gc.log.0", "[1.000s][info][gc] old first\n[2.000s][info][gc] old second\n"}
        });
        long baseline = descriptorsFor(archive);
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("[1.000s][info][gc] old first", lines.iterator().next());
        }
        assertEquals(baseline, descriptorsFor(archive));

        try (Stream<String> lines = logFile.stream()) {
            assertEquals(List.of(
                            "[1.000s][info][gc] old first",
                            "[2.000s][info][gc] old second",
                            "[3.000s][info][gc] current first",
                            "[4.000s][info][gc] current second",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String[][] entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (String[] entry : entries) {
                output.putNextEntry(new ZipEntry(entry[0]));
                output.write(entry[1].getBytes(StandardCharsets.UTF_8));
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

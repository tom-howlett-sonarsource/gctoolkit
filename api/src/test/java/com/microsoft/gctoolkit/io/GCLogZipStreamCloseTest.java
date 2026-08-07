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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GCLogZipStreamCloseTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesLinesAndSentinel() throws Exception {
        Path archive = zip("single.zip", entry("gc.log", " first \n\nsecond\n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(Arrays.asList("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentPreservesRawLines() throws Exception {
        Path archive = zip("segment.zip", entry("segment.log", " first \nsecond\n"));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(Arrays.asList(" first ", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipPreservesSegmentOrderAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(Arrays.asList(
                            "[0.500s][info][gc] older start",
                            "[1.000s][info][gc] older",
                            "[1.500s][info][gc] current start",
                            "[2.000s][info][gc] current",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void closingPartiallyConsumedRotatingZipClosesActiveArchive() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.500s][info][gc] older start", lines.iterator().next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingArchive() throws IOException {
        return zip("rotating.zip",
                entry("gc.log.0", "[0.500s][info][gc] older start\n[1.000s][info][gc] older\n"),
                entry("gc.log", "[1.500s][info][gc] current start\n[2.000s][info][gc] current\n"));
    }

    private ZipContents entry(String name, String contents) {
        return new ZipContents(name, contents);
    }

    private Path zip(String name, ZipContents... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            for (ZipContents entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(entry.contents.getBytes(StandardCharsets.UTF_8));
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

    private static final class ZipContents {
        private final String name;
        private final String contents;

        private ZipContents(String name, String contents) {
            this.name = name;
            this.contents = contents;
        }
    }
}

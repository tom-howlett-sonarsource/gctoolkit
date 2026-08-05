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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ZipBackedGCLogStreamTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipRetainsFilteredLinesAndSentinel() throws Exception {
        Path archive = zip("single.zip",
                entry("gc.log", "  first line  \n\nsecond line\n"));

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(Arrays.asList("first line", "second line", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentRetainsRawLineContents() throws Exception {
        Path archive = zip("segment.zip",
                entry("segment.log", "  first line  \n\nsecond line\n"));

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(Arrays.asList("  first line  ", "", "second line"),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipClosesAllArchivesAfterPartialConsumption() throws Exception {
        assumeTrue(Files.isDirectory(Path.of("/proc/self/fd")));
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.001s][info][gc] old first", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipRetainsSegmentOrderLinesAndSentinel() throws Exception {
        Path archive = rotatingArchive();
        List<String> expected = Arrays.asList(
                "[0.001s][info][gc] old first",
                "[0.002s][info][gc] old last",
                "[0.003s][info][gc] current first",
                "[0.004s][info][gc] current last",
                GCLogFile.END_OF_DATA_SENTINEL);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingArchive() throws IOException {
        return zip("rotating.zip",
                entry("gc.log.0", "[0.001s][info][gc] old first  \n\n[0.002s][info][gc] old last\n"),
                entry("gc.log", "[0.003s][info][gc] current first\n[0.004s][info][gc] current last  \n"));
    }

    private String[] entry(String name, String contents) {
        return new String[]{name, contents};
    }

    private Path zip(String name, String[]... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
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

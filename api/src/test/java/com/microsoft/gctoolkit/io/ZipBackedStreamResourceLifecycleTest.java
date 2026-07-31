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
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Supplements {@code VisibleZipStreamResourceLifecycleTest} with coverage that acceptance test
 * does not exercise: gzip-backed {@link SingleGCLogFile} streams, full (rather than partial)
 * consumption, repeated {@code close()} calls, and line-content/sentinel preservation.
 */
class ZipBackedStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGZipFileClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = gzip("single.log.gz", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            assertEquals(baseline + 1, descriptorsFor(archive), "the test must observe an open archive");
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(
                List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second", GCLogFile.END_OF_DATA_SENTINEL),
                lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void singleZipFileToleratesRepeatedClose() throws Exception {
        Path archive = zip("single.zip", "gc.log", "[0.001s][info][gc] only line\n");
        long baseline = descriptorsFor(archive);

        Stream<String> stream = new SingleGCLogFile(archive).stream();
        assertEquals("[0.001s][info][gc] only line", stream.findFirst().orElseThrow());
        stream.close();
        stream.close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentClosesArchiveAfterFullConsumptionAndPreservesContent() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "line one\nline two\nline three\n");
        long baseline = descriptorsFor(archive);

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(baseline + 1, descriptorsFor(archive), "the test must observe an open archive");
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("line one", "line two", "line three"), lines);
        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void zipSegmentToleratesRepeatedClose() throws Exception {
        Path archive = zip("segment2.zip", "segment.log", "line one\nline two\n");
        long baseline = descriptorsFor(archive);

        Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream();
        assertEquals("line one", stream.findFirst().orElseThrow());
        stream.close();
        stream.close();

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(contents.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); GZIPOutputStream output = new GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
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

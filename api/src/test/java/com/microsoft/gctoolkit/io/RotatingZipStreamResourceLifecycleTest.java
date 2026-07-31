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

class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void composedStreamPreservesSegmentOrderContentsAndSentinel() throws Exception {
        Path archive = rotatingZip();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(
                    List.of(
                            "[0.000s][info][gc] segment0 line one",
                            "[0.500s][info][gc] segment0 line two",
                            "[10.000s][info][gc] segment1 line one",
                            "[10.500s][info][gc] segment1 line two",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    collected);
        }
    }

    @Test
    void composedStreamClosesEveryArchiveHandleAfterFullConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            lines.forEach(line -> { /* drain fully */ });
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void composedStreamClosesEveryArchiveHandleAfterPartialConsumption() throws Exception {
        // The composed stream is built with flatMap over one GCLogFileZipSegment per entry: reading
        // only the first line short-circuits before the second segment's archive is ever opened, so
        // unlike the single-segment cases this does not leave a handle open for the test to observe
        // mid-stream -- the invariant under test is simply that closing early leaks nothing.
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals("[0.000s][info][gc] segment0 line one", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, "gc.log.1.current", "[10.000s][info][gc] segment1 line one\n[10.500s][info][gc] segment1 line two\n");
            addEntry(output, "gc.log.0", "[0.000s][info][gc] segment0 line one\n[0.500s][info][gc] segment0 line two\n");
        }
        return archive;
    }

    private void addEntry(ZipOutputStream output, String name, String contents) throws IOException {
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

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

class ZipStreamContentAndCompositionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleZipPreservesLinesAndEndOfDataSentinel() throws Exception {
        Path archive = zip("single.zip", "gc.log", " first \n\nsecond\n");

        try (Stream<String> lines = new SingleGCLogFile(archive).stream()) {
            assertEquals(List.of("first", "second", GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipSegmentPreservesUnfilteredLineContents() throws Exception {
        Path archive = zip("segment.zip", "segment.log", " first \n\nsecond\n");

        try (Stream<String> lines = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            assertEquals(List.of(" first ", "", "second"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rotatingZipPreservesOrderAndSentinelAndClosesAfterPartialConsumption() throws Exception {
        Path archive = zip("rotating.zip",
                "gc.log.0", "[0.001s][info][gc] oldest start\n[0.500s][info][gc] oldest end\n",
                "gc.log.1", "[1.001s][info][gc] middle start\n[1.500s][info][gc] middle end\n",
                "gc.log", "[2.001s][info][gc] newest start\n[2.500s][info][gc] newest end\n");
        RotatingGCLogFile rotatingLog = new RotatingGCLogFile(archive);

        try (Stream<String> lines = rotatingLog.stream()) {
            assertEquals(List.of(
                            "[0.001s][info][gc] oldest start",
                            "[0.500s][info][gc] oldest end",
                            "[1.001s][info][gc] middle start",
                            "[1.500s][info][gc] middle end",
                            "[2.001s][info][gc] newest start",
                            "[2.500s][info][gc] newest end",
                            GCLogFile.END_OF_DATA_SENTINEL),
                    lines.collect(Collectors.toList()));
        }

        long baseline = descriptorsFor(archive);
        try (Stream<String> lines = rotatingLog.stream()) {
            assertEquals("[0.001s][info][gc] oldest start", lines.findFirst().orElseThrow());
        }
        assertEquals(baseline, descriptorsFor(archive));
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

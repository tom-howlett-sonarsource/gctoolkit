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

class RotatingZipStreamClosureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void closesCurrentArchiveSegmentAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        RotatingGCLogFile logFile = new RotatingGCLogFile(archive);
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = logFile.stream()) {
            assertEquals("[0.001s][info][gc] oldest", lines.findFirst().orElseThrow());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void preservesSegmentOrderLineContentsAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(List.of(
                    "[0.001s][info][gc] oldest",
                    "[0.002s][info][gc] oldest detail",
                    "[1.001s][info][gc] middle",
                    "[1.002s][info][gc] middle detail",
                    "[2.001s][info][gc] current",
                    "[2.002s][info][gc] current detail",
                    GCLogFile.END_OF_DATA_SENTINEL), lines.collect(Collectors.toList()));
        }
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc-rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[0.001s][info][gc] oldest\n[0.002s][info][gc] oldest detail\n");
            writeEntry(output, "gc.log.1", "[1.001s][info][gc] middle\n[1.002s][info][gc] middle detail\n");
            writeEntry(output, "gc.log", "[2.001s][info][gc] current\n[2.002s][info][gc] current detail\n");
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

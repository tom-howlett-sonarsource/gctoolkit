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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipBackedRotatingGCLogFileResourceLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void closesAllArchiveResourcesAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe open archive resources");
            assertEquals("[1.000s][info][gc] older-first", lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "an unconsumed segment must remain open");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void preservesSegmentOrderLineContentsAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[1.000s][info][gc] older-first",
                "[1.500s][info][gc] older-last",
                "[2.000s][info][gc] current-first",
                "[3.000s][info][gc] current-last",
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            addEntry(output, "gc.log.0", "[1.000s][info][gc] older-first\n\n[1.500s][info][gc] older-last \n");
            addEntry(output, "gc.log.current", "[2.000s][info][gc] current-first\n[3.000s][info][gc] current-last\n");
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
                    continue;
                }
            }
        }
        return count;
    }
}

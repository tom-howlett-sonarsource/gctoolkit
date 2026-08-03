// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class RotatingZipStreamCloseTest {
    @TempDir
    Path directory;

    @Test
    void closesAllArchivesWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = archive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertTrue(lines.findFirst().isPresent());
            assertTrue(descriptorsFor(archive) > baseline);
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void retainsOrderedContentsAndEndOfDataSentinel() throws Exception {
        Path archive = archive();
        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.002s][info][gc] current", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path archive() throws Exception {
        Path archive = directory.resolve("gc.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "gc.log", "[0.002s][info][gc] current\n");
        }
        return archive;
    }

    private void add(ZipOutputStream zip, String name, String contents) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private long descriptorsFor(Path archive) throws Exception {
        Path expected = archive.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> descriptors = Files.newDirectoryStream(Path.of("/proc/self/fd"))) {
            for (Path descriptor : descriptors) {
                try {
                    if (Files.readSymbolicLink(descriptor).toString().replace(" (deleted)", "")
                            .equals(expected.toString())) count++;
                } catch (Exception ignored) {
                    // Descriptor may disappear during traversal.
                }
            }
        }
        return count;
    }
}

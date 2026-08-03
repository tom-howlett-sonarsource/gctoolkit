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

class RotatingZipStreamResourceLifecycleTest {

    private static final String OLDER_FIRST_LINE = "[0.500s][info][gc] older first";
    private static final String OLDER_LAST_LINE = "[1.000s][info][gc] older last";
    private static final String CURRENT_FIRST_LINE = "[2.000s][info][gc] current first";
    private static final String CURRENT_LAST_LINE = "[3.000s][info][gc] current last";

    @TempDir
    Path temporaryDirectory;

    @Test
    void closesArchiveAfterPartialConsumption() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            assertEquals(OLDER_FIRST_LINE, lines.findFirst().orElseThrow());
            assertTrue(descriptorsFor(archive) > baseline, "the test must observe an open archive");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void preservesEntryBoundariesOrderingAndSentinel() throws Exception {
        Path archive = rotatingArchive();

        List<String> lines;
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                OLDER_FIRST_LINE,
                OLDER_LAST_LINE,
                CURRENT_FIRST_LINE,
                CURRENT_LAST_LINE,
                GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("gc-rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.1", OLDER_FIRST_LINE + System.lineSeparator() + OLDER_LAST_LINE);
            writeEntry(output, "gc.log", CURRENT_FIRST_LINE + System.lineSeparator()
                    + CURRENT_LAST_LINE + System.lineSeparator());
        }
        return archive;
    }

    private static void writeEntry(ZipOutputStream output, String name, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(name));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private static long descriptorsFor(Path archive) throws IOException {
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

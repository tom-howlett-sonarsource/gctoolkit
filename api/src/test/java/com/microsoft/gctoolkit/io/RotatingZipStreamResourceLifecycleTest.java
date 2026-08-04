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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingZipStreamResourceLifecycleTest {

    private static final String OLD_FIRST = "[0.100s][info][gc] old first";
    private static final String OLD_SECOND = "[0.200s][info][gc] old second";
    private static final String CURRENT_FIRST = "[0.300s][info][gc] current first";
    private static final String CURRENT_SECOND = "[0.400s][info][gc] current second";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesArchivesAfterPartialConsumption() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals(OLD_FIRST, iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipPreservesSegmentOrderLinesAndEndOfData() throws Exception {
        Path archive = rotatingZip();
        long baseline = descriptorsFor(archive);

        List<String> actual;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(List.of(
                OLD_FIRST,
                OLD_SECOND,
                CURRENT_FIRST,
                CURRENT_SECOND,
                GCLogFile.END_OF_DATA_SENTINEL), actual);
        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive);
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", OLD_FIRST + "\n" + OLD_SECOND + "\n");
            writeEntry(output, "gc.log", CURRENT_FIRST + "\n" + CURRENT_SECOND + "\n");
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

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

class RotatingZipStreamResourceLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void closesEveryArchiveHandleWhenPartiallyConsumedStreamIsClosed() throws Exception {
        Path archive = rotatingArchive();
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            Iterator<String> iterator = lines.iterator();
            assertEquals("[0.001s][info][gc] oldest-first", iterator.next());
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void preservesSegmentOrderLineContentsAndEndOfDataSentinel() throws Exception {
        Path archive = rotatingArchive();

        List<String> actual;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            actual = lines.collect(Collectors.toList());
        }

        assertEquals(Arrays.asList(
                "[0.001s][info][gc] oldest-first",
                "[0.002s][info][gc] oldest-last",
                "[1.001s][info][gc] middle-first",
                "[1.002s][info][gc] middle-last",
                "[2.001s][info][gc] current-first",
                "[2.002s][info][gc] current-last",
                GCLogFile.END_OF_DATA_SENTINEL), actual);
    }

    private Path rotatingArchive() throws IOException {
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log.0", "[0.001s][info][gc] oldest-first\n[0.002s][info][gc] oldest-last\n");
            writeEntry(output, "gc.log.1", "[1.001s][info][gc] middle-first\n[1.002s][info][gc] middle-last\n");
            writeEntry(output, "gc.log", "[2.001s][info][gc] current-first\n[2.002s][info][gc] current-last\n");
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

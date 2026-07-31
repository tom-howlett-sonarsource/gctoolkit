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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotatingZipStreamResourceLifecycleTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rotatingZipClosesArchiveAfterPartialConsumption() throws Exception {
        // RotatingGCLogFile.stream() composes each segment's stream via flatMap, which
        // closes a segment's archive handle as soon as that segment has produced the
        // element a short-circuiting consumer (like findFirst()) is waiting for -
        // i.e. eagerly, before the caller's try-with-resources ever runs. So there is
        // no window in which an outside observer can see the handle still open; the
        // property under test is that no descriptor survives past partial consumption,
        // whether or not the stream is subsequently closed.
        Path archive = rotatingZip("rotating.zip");
        long baseline = descriptorsFor(archive);

        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            String first = lines.findFirst().orElseThrow();
            assertEquals("[0.001s][info][gc] oldest-first", first);
            assertEquals(baseline, descriptorsFor(archive), "the segment archive must already be released");
        }

        assertEquals(baseline, descriptorsFor(archive));
    }

    @Test
    void rotatingZipClosesArchiveAfterFullConsumption() throws Exception {
        Path archive = rotatingZip("rotating-full.zip");
        long baseline = descriptorsFor(archive);

        long lineCount;
        try (Stream<String> lines = new RotatingGCLogFile(archive).stream()) {
            lineCount = lines.count();
        }

        assertEquals(7, lineCount); // 6 log lines + END_OF_DATA_SENTINEL
        assertEquals(baseline, descriptorsFor(archive));
    }

    /**
     * Ordering the segments requires opening each zip entry to read its start/end
     * timestamps; this must not leave any of those archive resources open either.
     */
    @Test
    void orderingSegmentsDoesNotLeakArchiveDescriptors() throws Exception {
        Path archive = rotatingZip("rotating-ordering.zip");
        long baseline = descriptorsFor(archive);

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);
        rotatingGCLogFile.getOrderedGarbageCollectionLogFiles();

        assertEquals(baseline, descriptorsFor(archive));
    }

    private Path rotatingZip(String name) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log", "[2.001s][info][gc] current-first\n[2.002s][info][gc] current-second\n");
            writeEntry(output, "gc.log.0", "[1.001s][info][gc] middle-first\n[1.002s][info][gc] middle-second\n");
            writeEntry(output, "gc.log.1", "[0.001s][info][gc] oldest-first\n[0.002s][info][gc] oldest-second\n");
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
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

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZipBackedGCLogStreamContentTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void singleGCLogFilePreservesLineContentAndSentinel() throws Exception {
        Path archive = zip("single.zip", "gc.log", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] first",
                "[0.002s][info][gc] second",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
    }

    @Test
    void singleGCLogFileGZipPreservesLineContentAndSentinel() throws Exception {
        Path archive = gzip("single.gz", "[0.001s][info][gc] first\n[0.002s][info][gc] second\n");

        List<String> lines;
        try (Stream<String> stream = new SingleGCLogFile(archive).stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] first",
                "[0.002s][info][gc] second",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
    }

    @Test
    void zipSegmentPreservesLineContentVerbatim() throws Exception {
        Path archive = zip("segment.zip", "segment.log", "[0.001s][info][gc] first\nsecond\n");

        List<String> lines;
        try (Stream<String> stream = new GCLogFileZipSegment(archive, "segment.log").stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of("[0.001s][info][gc] first", "second"), lines);
    }

    @Test
    void rotatingGCLogFilePreservesSegmentOrderingContentAndSentinel() throws Exception {
        // Entries are written out of rotation order to verify that the composed
        // stream reorders them oldest-first, ending with the current segment.
        Path archive = temporaryDirectory.resolve("rotating.zip");
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, "gc.log", "[2.001s][info][gc] current-first\n[2.002s][info][gc] current-second\n");
            writeEntry(output, "gc.log.0", "[1.001s][info][gc] middle-first\n[1.002s][info][gc] middle-second\n");
            writeEntry(output, "gc.log.1", "[0.001s][info][gc] oldest-first\n[0.002s][info][gc] oldest-second\n");
        }

        RotatingGCLogFile rotatingGCLogFile = new RotatingGCLogFile(archive);

        List<String> orderedSegmentNames = rotatingGCLogFile.getOrderedGarbageCollectionLogFiles()
                .stream()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList());
        assertEquals(List.of("gc.log.1", "gc.log.0", "gc.log"), orderedSegmentNames);

        List<String> lines;
        try (Stream<String> stream = rotatingGCLogFile.stream()) {
            lines = stream.collect(Collectors.toList());
        }

        assertEquals(List.of(
                "[0.001s][info][gc] oldest-first",
                "[0.002s][info][gc] oldest-second",
                "[1.001s][info][gc] middle-first",
                "[1.002s][info][gc] middle-second",
                "[2.001s][info][gc] current-first",
                "[2.002s][info][gc] current-second",
                GCLogFile.END_OF_DATA_SENTINEL
        ), lines);
    }

    private Path zip(String name, String entryName, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive); ZipOutputStream output = new ZipOutputStream(bytes)) {
            writeEntry(output, entryName, contents);
        }
        return archive;
    }

    private void writeEntry(ZipOutputStream output, String entryName, String contents) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private Path gzip(String name, String contents) throws IOException {
        Path archive = temporaryDirectory.resolve(name);
        try (OutputStream bytes = Files.newOutputStream(archive);
             java.util.zip.GZIPOutputStream output = new java.util.zip.GZIPOutputStream(bytes)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
        return archive;
    }
}

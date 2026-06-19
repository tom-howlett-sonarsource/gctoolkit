// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static java.util.stream.Collectors.toList;

public class LogSourceSegmentsTest {

    private static final String LOG_FILE_NAME = "gc.log";
    private static final String FIRST_SEGMENT_NAME = "gc.log.0";
    private static final String SECOND_SEGMENT_NAME = "gc.log.1";
    private static final String THIRD_LINE = "three";

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversDirectorySegments() throws IOException {
        Path first = Files.writeString(temporaryDirectory.resolve(FIRST_SEGMENT_NAME), "first");
        Path second = Files.writeString(temporaryDirectory.resolve(SECOND_SEGMENT_NAME), "second");

        List<Path> segments = LogSourceSegments.listDirectory(temporaryDirectory);

        assertEquals(List.of(first, second), segments.stream().sorted().collect(toList()));
    }

    @Test
    void discoversZipSegments() throws IOException {
        Path zipFile = temporaryDirectory.resolve("gc.zip");
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zipOutputStream.putNextEntry(new ZipEntry(FIRST_SEGMENT_NAME));
            zipOutputStream.write("first".getBytes());
            zipOutputStream.closeEntry();
            zipOutputStream.putNextEntry(new ZipEntry(SECOND_SEGMENT_NAME));
            zipOutputStream.write("second".getBytes());
            zipOutputStream.closeEntry();
        }

        assertEquals(List.of(FIRST_SEGMENT_NAME, SECOND_SEGMENT_NAME), LogSourceSegments.listZipEntries(zipFile));
    }

    @Test
    void readsBoundedTailFromPlainTextFiles() throws IOException {
        Path logFile = temporaryDirectory.resolve(LOG_FILE_NAME);
        Files.writeString(logFile, "one\ntwo\n" + THIRD_LINE + "\nfour\n");

        assertEquals(List.of("two", THIRD_LINE, "four"), LogSourceSegments.tail(logFile, 3));
    }

    @Test
    void readsSingleLineTailFromPlainTextFiles() throws IOException {
        Path logFile = temporaryDirectory.resolve(LOG_FILE_NAME);
        Files.writeString(logFile, "one");

        assertEquals(List.of("one"), LogSourceSegments.tail(logFile, 3));
    }

    @Test
    void returnsEmptyTailForEmptyFiles() throws IOException {
        Path logFile = temporaryDirectory.resolve(LOG_FILE_NAME);
        Files.writeString(logFile, "");

        assertEquals(List.of(), LogSourceSegments.tail(logFile, 3));
    }

    @Test
    void returnsEmptyTailForNonPositiveLimits() throws IOException {
        Path logFile = temporaryDirectory.resolve(LOG_FILE_NAME);
        Files.writeString(logFile, "one\ntwo\n");

        assertEquals(List.of(), LogSourceSegments.tail(logFile, 0));
    }

    @Test
    void collectorKeepsLastItems() {
        List<String> tail = Arrays.asList("one", "two", THIRD_LINE, "four")
                .stream()
                .collect(LogSourceSegments.tail(2));

        assertEquals(List.of(THIRD_LINE, "four"), tail);
    }
}

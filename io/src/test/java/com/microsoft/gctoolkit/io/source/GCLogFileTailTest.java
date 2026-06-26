// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileTailTest {

    private static final String LOG_FILE = "gc.log";
    private static final String THREE = "three";

    @Test
    void collectorRetainsOnlyTheLastNElements() {
        List<Integer> lastThree = IntStream.rangeClosed(1, 10)
                .boxed()
                .collect(GCLogFileTail.collector(3));

        assertEquals(List.of(8, 9, 10), lastThree);
    }

    @Test
    void collectorKeepsEveryElementWhenFewerThanN() {
        List<Integer> all = IntStream.rangeClosed(1, 2)
                .boxed()
                .collect(GCLogFileTail.collector(5));

        assertEquals(List.of(1, 2), all);
    }

    @Test
    void collectorRejectsNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> GCLogFileTail.collector(-1));
    }

    @Test
    void readReturnsTheLastLinesOfAFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, List.of("one", "two", THREE, "four", "five"));

        assertEquals(List.of("four", "five"), GCLogFileTail.read(file, 2));
    }

    @Test
    void readReturnsAllLinesWhenAskedForMoreThanExist(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, List.of("one", "two"));

        assertEquals(List.of("one", "two"), GCLogFileTail.read(file, 100));
    }

    @Test
    void readRejectsNegativeCount(@TempDir Path dir) {
        Path file = dir.resolve(LOG_FILE);
        assertThrows(IllegalArgumentException.class, () -> GCLogFileTail.read(file, -1));
    }

    @Test
    void readReleasesFileHandle(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, List.of("one", "two", THREE));

        assertEquals(List.of("two", THREE), GCLogFileTail.read(file, 2));

        assertTrue(Files.deleteIfExists(file));
    }

    @Test
    void readHandlesWindowsLineEndings(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, "one\r\ntwo\r\nthree\r\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("two", THREE), GCLogFileTail.read(file, 2));
    }

    @Test
    void readReturnsTheSoleLineOfAFileWithoutALineEnding(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, "only line".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("only line"), GCLogFileTail.read(file, 10));
    }

    @Test
    void readReturnsNothingForAnEmptyFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, new byte[0]);

        assertTrue(GCLogFileTail.read(file, 5).isEmpty());
    }

    @Test
    void readReturnsNothingWhenZeroLinesRequested(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(LOG_FILE);
        Files.write(file, List.of("one", "two"));

        assertTrue(GCLogFileTail.read(file, 0).isEmpty());
    }
}

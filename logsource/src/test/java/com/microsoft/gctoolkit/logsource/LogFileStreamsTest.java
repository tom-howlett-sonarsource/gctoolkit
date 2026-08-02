// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileStreamsTest {

    private static final List<String> EXPECTED = List.of(TestSources.LINE_ONE, TestSources.LINE_TWO);

    @TempDir
    Path directory;

    @Test
    void streamsPlainTextSources() throws IOException {
        assertEquals(EXPECTED, collect(TestSources.plainText(directory, "gc.log")));
    }

    @Test
    void streamsGZipSources() throws IOException {
        assertEquals(EXPECTED, collect(TestSources.gzip(directory, "gc.log.gz")));
    }

    @Test
    void streamsTheFirstEntryOfAZipSource() throws IOException {
        Path zip = TestSources.zip(directory, "gc.zip", Map.of("gc.log", TestSources.CONTENT));
        assertEquals(EXPECTED, collect(zip));
    }

    @Test
    void streamsANamedEntryOfAZipSource() throws IOException {
        Path zip = TestSources.zip(directory, "rotating.zip",
                Map.of("gc.log.0", "zero\n", "gc.log.1", "one\n"));
        try (Stream<String> lines = LogFileStreams.zipEntryLines(zip, "gc.log.1")) {
            assertEquals(List.of("one"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void streamsEveryEntryOfAZipSource() throws IOException {
        Path zip = TestSources.zip(directory, "rotating.zip", "gc.log.0", "gc.log.1");
        try (Stream<String> lines = LogFileStreams.allZipEntryLines(zip)) {
            List<String> collected = lines.collect(Collectors.toList());
            assertEquals(4, collected.size());
            assertTrue(collected.stream().allMatch(EXPECTED::contains));
        }
    }

    @Test
    void aDirectoryCannotBeStreamed() {
        IOException ioe = assertThrows(IOException.class, () -> LogFileStreams.lines(directory));
        assertTrue(ioe.getMessage().contains(directory.toString()));
    }

    @Test
    void tailReadsTheLastLinesOfASource() throws IOException {
        Path path = TestSources.plainText(directory, "gc.log", "one\ntwo\nthree\nfour\n");
        assertEquals(List.of("three", "four"), LogFileStreams.tail(path, 2));
    }

    @Test
    void tailOfAnEmptySourceIsEmpty() throws IOException {
        Path path = TestSources.plainText(directory, "empty.log", "");
        assertTrue(LogFileStreams.tail(path, 10).isEmpty());
    }

    private List<String> collect(Path path) throws IOException {
        try (Stream<String> lines = LogFileStreams.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}

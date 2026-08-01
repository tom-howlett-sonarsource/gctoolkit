// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileTailTest {

    @TempDir
    Path directory;

    @Test
    void readsTheLastLinesOfASource() throws IOException {
        Path path = LogSourceFixture.plainText(directory, "gc.log", numberedLines(20, "\n"));

        List<String> tail = LogFileTail.lastLines(path, 3);

        assertTrue(tail.size() >= 3, "expected at least the three lines that were asked for");
        assertEquals(List.of("line 17", "line 18", "line 19"), tail.subList(tail.size() - 3, tail.size()));
    }

    @Test
    void readsBackToTheStartWhenTheSourceIsShorterThanTheTail() throws IOException {
        Path path = LogSourceFixture.plainText(directory, "gc.log", numberedLines(3, "\n"));

        List<String> tail = LogFileTail.lastLines(path, 100);

        assertEquals(3, tail.size(), "the backwards read stops at the start of the source");
        assertEquals(List.of("line 1", "line 2"), tail.subList(1, 3));
    }

    @Test
    void readsASourceWithWindowsLineEndings() throws IOException {
        Path path = LogSourceFixture.plainText(directory, "gc.log", numberedLines(5, "\r\n"));

        List<String> tail = LogFileTail.lastLines(path, 3);

        assertEquals("line 4", tail.get(tail.size() - 1));
        assertTrue(tail.contains("line 3"));
    }

    @Test
    void readsNothingFromAnEmptySource() throws IOException {
        assertEquals(List.of(), LogFileTail.lastLines(LogSourceFixture.empty(directory, "empty.log"), 10));
    }

    @Test
    void readsNothingFromASourceWithoutALineEnding() throws IOException {
        Path path = LogSourceFixture.plainText(directory, "gc.log", "line 0");

        assertEquals(List.of(), LogFileTail.lastLines(path, 10));
    }

    @Test
    void collectsTheLastElementsOfAStream() {
        List<String> last = Stream.of("a", "b", "c", "d").collect(LogFileTail.lastElements(2));

        assertEquals(List.of("c", "d"), last);
    }

    @Test
    void collectsEverythingWhenTheStreamIsShorterThanTheWindow() {
        assertEquals(List.of("a", "b"), Stream.of("a", "b").collect(LogFileTail.lastElements(5)));
    }

    @Test
    void collectsTheLastElementsOfAParallelStream() {
        List<Integer> source = IntStream.range(0, 1000).boxed().collect(Collectors.toList());

        List<Integer> last = source.parallelStream().collect(LogFileTail.lastElements(3));

        assertEquals(List.of(997, 998, 999), last);
    }

    private String numberedLines(int count, String lineEnding) {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < count; i++)
            content.append("line ").append(i).append(lineEnding);
        return content.toString();
    }
}

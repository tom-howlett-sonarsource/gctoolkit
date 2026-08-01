// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileTailTest {

    @TempDir
    Path directory;

    @Test
    void readsTheLastLinesOfASource() throws IOException {
        Path path = write("one\ntwo\nthree\nfour\n");
        assertEquals(List.of("three", "four"), LogFileTail.lastLines(path, 2));
    }

    @Test
    void readsWhatItCanWhenAskedForMoreLinesThanTheSourceHolds() throws IOException {
        // the backwards scan stops at the second byte of the source, clipping the first line
        Path path = write("one\ntwo\nthree\n");
        assertEquals(List.of("ne", "two", "three"), LogFileTail.lastLines(path, 100));
    }

    @Test
    void readsNoLinesFromAnEmptySource() throws IOException {
        assertTrue(LogFileTail.lastLines(write(""), 10).isEmpty());
    }

    @Test
    void readsNoLinesFromASourceHoldingASingleLine() throws IOException {
        assertTrue(LogFileTail.lastLines(write("one\n"), 10).isEmpty());
    }

    @Test
    void readsNoLinesFromASourceWithoutLineEndings() throws IOException {
        assertTrue(LogFileTail.lastLines(write("a single, unterminated line"), 10).isEmpty());
    }

    @Test
    void readsNoLinesFromASourceThatCannotBeSized() throws IOException {
        assertTrue(LogFileTail.lastLines(directory.resolve("does-not-exist.log"), 10).isEmpty());
    }

    @Test
    void collectsTheLastElementsOfAStream() {
        assertEquals(List.of("three", "four"), Stream.of("one", "two", "three", "four").collect(LogFileTail.lastN(2)));
    }

    @Test
    void collectsEveryElementOfAShortStream() {
        assertEquals(List.of("one", "two"), Stream.of("one", "two").collect(LogFileTail.lastN(10)));
    }

    @Test
    void collectsTheLastElementsOfAParallelStream() {
        List<String> lines = Stream.of("one", "two", "three", "four", "five")
                .parallel()
                .collect(LogFileTail.lastN(2));
        assertEquals(List.of("four", "five"), lines);
    }

    private Path write(String content) throws IOException {
        Path path = directory.resolve("gc.log");
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        return path;
    }
}

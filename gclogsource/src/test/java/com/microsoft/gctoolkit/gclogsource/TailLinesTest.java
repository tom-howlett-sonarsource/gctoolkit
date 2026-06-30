// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TailLinesTest {

    private static final int LARGE_LINE_LENGTH = (1024 * 1024) + 1;

    @TempDir
    private Path tempDir;

    @Test
    void keepsLastLinesInOriginalOrder() {
        List<Integer> tail = Stream.of(1, 2, 3, 4).collect(TailLines.tail(2));

        assertEquals(List.of(3, 4), tail);
    }

    @Test
    void zeroLinesReturnsEmptyList() {
        List<Integer> tail = Stream.of(1, 2, 3, 4).collect(TailLines.tail(0));

        assertEquals(List.of(), tail);
    }

    @Test
    void rejectsNegativeLineCount() {
        assertThrows(IllegalArgumentException.class, () -> TailLines.tail(-1));
    }

    @Test
    void readsTailFromFile() throws IOException {
        Path file = tempDir.resolve("tail.log");
        Files.write(file, List.of("first", "second", "third"));

        assertEquals(List.of("second", "third"), TailLines.from(file, 2));
    }

    @Test
    void readsEmptyTailFromEmptyFile() throws IOException {
        Path file = Files.createFile(tempDir.resolve("empty.log"));

        assertEquals(List.of(), TailLines.from(file, 2));
    }

    @Test
    void doesNotLoadLargeLeadingLineIntoTail() throws IOException {
        Path file = tempDir.resolve("large-leading-line.log");
        Files.writeString(file, "a".repeat(LARGE_LINE_LENGTH) + "\nlast\n");

        assertEquals(List.of("last"), TailLines.from(file, 2));
    }
}

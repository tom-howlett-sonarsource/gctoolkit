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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.microsoft.gctoolkit.logsource.LogSources.FIRST_LINE;
import static com.microsoft.gctoolkit.logsource.LogSources.LAST_LINE;
import static com.microsoft.gctoolkit.logsource.LogSources.writeGzip;
import static com.microsoft.gctoolkit.logsource.LogSources.writePlainText;
import static com.microsoft.gctoolkit.logsource.LogSources.writeZip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceStreamsTest {

    private static final List<String> EXPECTED_LINES = List.of(FIRST_LINE, LAST_LINE);

    @TempDir
    Path directory;

    @Test
    void streamsAPlainTextSource() throws IOException {
        assertEquals(EXPECTED_LINES, linesOf(writePlainText(directory, "gc.log")));
    }

    @Test
    void streamsAGZipSource() throws IOException {
        assertEquals(EXPECTED_LINES, linesOf(writeGzip(directory, "gc.log.gz")));
    }

    @Test
    void streamsTheFirstFileEntryOfAZipSource() throws IOException {
        assertEquals(EXPECTED_LINES, linesOf(writeZip(directory, "gc.zip", "segments/", "segments/gc.log")));
    }

    @Test
    void streamsANamedEntryOfAZipSource() throws IOException {
        Path path = writeZip(directory, "gc.zip", "gc.log", "gc.log.1");

        try (Stream<String> lines = LogSourceStreams.zipEntryLines(path, "gc.log.1")) {
            assertEquals(EXPECTED_LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void refusesToStreamAnEntryThatIsNotInTheZipSource() throws IOException {
        Path path = writeZip(directory, "gc.zip", "gc.log");

        assertThrows(IOException.class, () -> LogSourceStreams.zipEntryLines(path, "missing.log"));
    }

    @Test
    void refusesToStreamADirectory() {
        IOException ioe = assertThrows(IOException.class, () -> LogSourceStreams.lines(directory));
        assertEquals("Unable to read " + directory, ioe.getMessage());
    }

    @Test
    void refusesToStreamASourceOfUnknownFormat() throws IOException {
        Path path = writePlainText(directory, "gc.log");

        assertThrows(IOException.class, () -> LogSourceStreams.lines(path, LogSourceFormat.UNKNOWN));
    }

    @Test
    void refusesToStreamAPlainTextSourceAsGZip() throws IOException {
        Path path = writePlainText(directory, "gc.log");

        assertThrows(IOException.class, () -> LogSourceStreams.lines(path, LogSourceFormat.GZIP));
    }

    @Test
    void readsTheTailOfASource() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, "one\ntwo\nthree\n", StandardCharsets.UTF_8);

        assertEquals(List.of("two", "three"), LogSourceStreams.tail(path, 2));
    }

    @Test
    void readsTheTailOfASourceWithWindowsLineEndings() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, "one\r\ntwo\r\nthree\r\n", StandardCharsets.UTF_8);

        assertEquals(List.of("two", "three"), LogSourceStreams.tail(path, 2));
    }

    @Test
    void readsTheWholeSourceWhenItIsShorterThanTheRequestedTail() throws IOException {
        assertEquals(EXPECTED_LINES, LogSourceStreams.tail(writePlainText(directory, "gc.log"), 100));
    }

    @Test
    void readsTheTailOfASourceThatDoesNotEndWithALineTerminator() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, "one\ntwo\nthree", StandardCharsets.UTF_8);

        assertEquals(List.of("two", "three"), LogSourceStreams.tail(path, 2));
    }

    @Test
    void readsAnEmptyTailFromAnEmptySource() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, "", StandardCharsets.UTF_8);

        assertTrue(LogSourceStreams.tail(path, 10).isEmpty());
    }

    @Test
    void collectsTheLastLinesOfAStream() {
        assertEquals(List.of("d", "e"), Stream.of("a", "b", "c", "d", "e").collect(LogSourceStreams.lastLines(2)));
    }

    @Test
    void collectsEveryLineWhenTheStreamIsShorterThanTheRequestedTail() {
        assertEquals(List.of("a", "b"), Stream.of("a", "b").collect(LogSourceStreams.lastLines(5)));
    }

    @Test
    void collectsTheLastLinesOfAParallelStream() {
        List<String> lines = Stream.of("a", "b", "c", "d", "e").parallel().collect(LogSourceStreams.lastLines(2));

        assertEquals(List.of("d", "e"), lines);
    }

    private List<String> linesOf(Path path) throws IOException {
        try (Stream<String> lines = LogSourceStreams.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceFilesTest {

    private static final String GC_LOG = "gc.log";
    private static final String FIRST_ROTATION = "gc.log.1";
    private static final String LINE = "line";
    private static final String ONE = "one";
    private static final String TWO = "two";
    private static final String THREE = "three";
    private static final String FOUR = "four";

    @TempDir
    Path directory;

    @Test
    void reportsTheSizeOfASource() throws IOException {
        Path path = LogSources.plainText(directory, GC_LOG, "12345");
        assertEquals(5L, LogSourceFiles.sizeInBytes(path));
    }

    @Test
    void reportsNoSizeForAnUnreadableSource() {
        assertEquals(0L, LogSourceFiles.sizeInBytes(directory.resolve("absent.log")));
    }

    @Test
    void discoversTheSourcesInADirectory() throws IOException {
        LogSources.plainText(directory, GC_LOG, LINE);
        LogSources.plainText(directory, FIRST_ROTATION, LINE);

        List<String> names = LogSourceFiles.list(directory).stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(GC_LOG, FIRST_ROTATION), names);
    }

    @Test
    void failsToDiscoverTheSourcesInAMissingDirectory() {
        assertThrows(IOException.class, () -> LogSourceFiles.list(directory.resolve("absent")));
    }

    @Test
    void discoversTheFileEntriesInAZipSource() throws IOException {
        Path path = LogSources.zip(directory, "gc.zip", "gc.log.0", FIRST_ROTATION);
        assertEquals(List.of("gc.log.0", FIRST_ROTATION), LogSourceFiles.zipEntryNames(path));
    }

    @Test
    void failsToDiscoverTheEntriesOfASourceThatIsNotZipCompressed() throws IOException {
        Path path = LogSources.plainText(directory, GC_LOG, "not a zip file");
        assertThrows(IOException.class, () -> LogSourceFiles.zipEntryNames(path));
    }

    @Test
    void readsTheLastLinesOfASource() throws IOException {
        Path path = LogSources.plainText(directory, GC_LOG, ONE, TWO, THREE, FOUR, "");
        assertEquals(List.of(THREE, FOUR), LogSourceFiles.tail(path, 2));
    }

    @Test
    void readsNoMoreLinesThanASourceHolds() throws IOException {
        Path path = LogSources.plainText(directory, GC_LOG, ONE, TWO, "");
        assertEquals(List.of(ONE, TWO), LogSourceFiles.tail(path, 100));
    }

    @Test
    void readsTheLastLinesOfASourceWrittenOnWindows() throws IOException {
        Path path = directory.resolve(GC_LOG);
        Files.write(path, "one\r\ntwo\r\nthree\r\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(TWO, THREE), LogSourceFiles.tail(path, 2));
    }

    @Test
    void readsNoLinesFromASourceThatHoldsNoLineEnd() throws IOException {
        Path path = LogSources.plainText(directory, GC_LOG, "a single unterminated line");
        assertTrue(LogSourceFiles.tail(path, 10).isEmpty());
    }

    @Test
    void collectsTheLastElementsOfAStream() {
        assertEquals(List.of(THREE, FOUR),
                Stream.of(ONE, TWO, THREE, FOUR).collect(LogSourceFiles.tailCollector(2)));
    }

    @Test
    void collectsNoMoreElementsThanAStreamHolds() {
        assertEquals(List.of(ONE, TWO), Stream.of(ONE, TWO).collect(LogSourceFiles.tailCollector(10)));
    }

    @Test
    void collectsTheLastElementsOfAParallelStream() {
        assertEquals(List.of(THREE, FOUR),
                Stream.of(ONE, TWO, THREE, FOUR).parallel().collect(LogSourceFiles.tailCollector(2)));
    }
}

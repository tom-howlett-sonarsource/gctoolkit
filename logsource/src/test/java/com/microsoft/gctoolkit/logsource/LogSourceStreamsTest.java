// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourceStreamsTest {

    private static final String FIRST_LINE = "0.001: first line";
    private static final String SECOND_LINE = "0.002: second line";
    private static final List<String> LINES = List.of(FIRST_LINE, SECOND_LINE);

    private static final String ZIP_NAME = "gc.zip";
    private static final String FIRST_ENTRY = "gc.log.0";
    private static final String SECOND_ENTRY = "gc.log.1";

    @TempDir
    Path directory;

    private static List<String> read(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }

    private static List<String> contentOf(String entryName) {
        return List.of("line in " + entryName);
    }

    @Test
    void readsAnUncompressedSource() throws IOException {
        Path path = LogSources.plainText(directory, "gc.log", FIRST_LINE, SECOND_LINE);
        assertEquals(LINES, read(LogSourceStreams.plainTextLines(path)));
        assertEquals(LINES, read(LogSourceStreams.lines(path)));
    }

    @Test
    void readsAGZipCompressedSource() throws IOException {
        Path path = LogSources.gzip(directory, "gc.log.gz", FIRST_LINE, SECOND_LINE);
        assertEquals(LINES, read(LogSourceStreams.lines(path)));
    }

    @Test
    void readsTheFirstFileEntryOfAZipCompressedSource() throws IOException {
        Path path = LogSources.zip(directory, ZIP_NAME, FIRST_ENTRY, SECOND_ENTRY);
        assertEquals(contentOf(FIRST_ENTRY), read(LogSourceStreams.lines(path)));
    }

    @Test
    void readsANamedEntryOfAZipCompressedSource() throws IOException {
        Path path = LogSources.zip(directory, ZIP_NAME, FIRST_ENTRY, SECOND_ENTRY);
        assertEquals(contentOf(SECOND_ENTRY), read(LogSourceStreams.zipEntryLines(path, SECOND_ENTRY)));
    }

    @Test
    void readsNothingFromAZipCompressedSourceHoldingNoFileEntry() throws IOException {
        Path path = LogSources.zip(directory, ZIP_NAME);
        assertEquals(List.of(), read(LogSourceStreams.lines(path)));
    }

    @Test
    void failsToReadAnEntryAZipCompressedSourceDoesNotHold() throws IOException {
        Path path = LogSources.zip(directory, ZIP_NAME, FIRST_ENTRY);
        assertThrows(IOException.class, () -> LogSourceStreams.zipEntryLines(path, "absent.log"));
    }

    @Test
    void failsToReadASourceOfAnUnknownFormat() {
        Path path = directory.resolve("gc.log");
        assertThrows(IOException.class, () -> LogSourceStreams.lines(path, LogSourceFormat.UNKNOWN));
    }

    @Test
    void failsToReadADirectoryAsASingleSource() {
        assertThrows(IOException.class, () -> LogSourceStreams.lines(directory));
    }

    @Test
    void releasesACompressedSourceWhenTheStreamIsClosed() throws IOException {
        Path path = LogSources.zip(directory, ZIP_NAME, FIRST_ENTRY);
        for (int attempt = 0; attempt < 64; attempt++) {
            assertEquals(contentOf(FIRST_ENTRY), read(LogSourceStreams.zipEntryLines(path, FIRST_ENTRY)));
        }
    }
}

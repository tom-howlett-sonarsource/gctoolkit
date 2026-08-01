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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileStreamsTest {

    private static final List<String> EXPECTED = List.of(LogSourceFixture.FIRST_LINE, LogSourceFixture.SECOND_LINE);

    @TempDir
    Path directory;

    @Test
    void opensPlainTextSources() throws IOException {
        assertEquals(EXPECTED, linesOf(LogSourceFixture.plainText(directory, "gc.log")));
    }

    @Test
    void opensGZipSources() throws IOException {
        assertEquals(EXPECTED, linesOf(LogSourceFixture.gzip(directory, "gc.log.gz")));
    }

    @Test
    void opensTheFirstNonDirectoryEntryOfAZipSource() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0", "gc.log.1");
        assertEquals(linesIn("gc.log.0"), linesOf(zip));
    }

    @Test
    void opensANamedEntryOfAZipSource() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0", "gc.log.1");
        try (Stream<String> lines = LogFileStreams.zipEntryLines(zip, "gc.log.1")) {
            assertEquals(linesIn("gc.log.1"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void rejectsAnEntryThatIsNotInTheZipSource() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0");
        IOException raised = assertThrows(IOException.class, () -> LogFileStreams.zipEntryLines(zip, "absent.log"));
        assertTrue(raised.getMessage().contains("absent.log"));
    }

    @Test
    void opensEveryEntryOfAZipSourceAsOneStream() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0", "gc.log.1");
        try (Stream<String> lines = LogFileStreams.allZipEntryLines(zip)) {
            List<String> expected = Stream.concat(linesIn("gc.log.0").stream(), linesIn("gc.log.1").stream())
                    .collect(Collectors.toList());
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void refusesToOpenADirectory() {
        IOException raised = assertThrows(IOException.class, () -> LogFileStreams.lines(directory));
        assertTrue(raised.getMessage().startsWith("Unable to read "));
    }

    @Test
    void refusesToOpenAnUnknownFormat() {
        IOException raised = assertThrows(IOException.class,
                () -> LogFileStreams.lines(directory.resolve("gc.log"), LogFileFormat.UNKNOWN));
        assertTrue(raised.getMessage().startsWith("Unable to read "));
    }

    @Test
    void releasesTheSourceWhenTheStreamIsClosed() throws IOException {
        Path zip = LogSourceFixture.zip(directory, "gc.zip", "gc.log.0");
        try (Stream<String> lines = LogFileStreams.zipEntryLines(zip, "gc.log.0")) {
            assertEquals(linesIn("gc.log.0"), lines.collect(Collectors.toList()));
        }
        // The archive is no longer held open, so it can be replaced.
        LogSourceFixture.zip(directory, "gc.zip", "gc.log.1");
        assertEquals(List.of("gc.log.1"), LogFileSources.zipEntryNames(zip));
    }

    private List<String> linesOf(Path path) throws IOException {
        try (Stream<String> lines = LogFileStreams.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private List<String> linesIn(String entryName) {
        return LogSourceFixture.contentOf(entryName).lines().collect(Collectors.toList());
    }
}

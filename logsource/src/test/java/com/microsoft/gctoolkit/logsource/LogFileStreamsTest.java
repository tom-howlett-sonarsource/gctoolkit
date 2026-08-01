// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileStreamsTest {

    @TempDir
    Path directory;

    @Test
    void streamsPlainTextSource() throws IOException {
        Path path = LogSourceFixture.writePlainText(directory, "gc.log");
        assertEquals(List.of(LogSourceFixture.LINE_ONE, LogSourceFixture.LINE_TWO), lines(path, LogFileFormat.PLAINTEXT));
    }

    @Test
    void streamsGZipSource() throws IOException {
        Path path = LogSourceFixture.writeGZip(directory, "gc.log.gz");
        assertEquals(List.of(LogSourceFixture.LINE_ONE, LogSourceFixture.LINE_TWO), lines(path, LogFileFormat.GZIP));
    }

    @Test
    void streamsFirstEntryOfZipSource() throws IOException {
        Path path = LogSourceFixture.writeZip(directory, "gc.zip", "gc.log.0", "gc.log.1");
        List<String> lines = lines(path, LogFileFormat.ZIP);
        assertEquals("gc.log.0", lines.get(0));
        assertEquals(3, lines.size());
    }

    @Test
    void streamsNamedEntryOfZipSource() throws IOException {
        Path path = LogSourceFixture.writeZip(directory, "gc.zip", "gc.log.0", "gc.log.1");
        try (Stream<String> lines = LogFileStreams.zipEntryLines(path, "gc.log.1")) {
            assertEquals("gc.log.1", lines.findFirst().orElseThrow());
        }
    }

    @Test
    void streamsNoLinesWhenZipSourceHoldsOnlyDirectories() throws IOException {
        Path path = LogSourceFixture.writeZip(directory, "empty.zip");
        assertTrue(lines(path, LogFileFormat.ZIP).isEmpty());
    }

    @Test
    void refusesUnknownEntryOfZipSource() throws IOException {
        Path path = LogSourceFixture.writeZip(directory, "gc.zip", "gc.log.0");
        assertThrows(IOException.class, () -> LogFileStreams.zipEntryLines(path, "missing.log"));
    }

    @Test
    void refusesToStreamADirectoryAsASingleLog() throws IOException {
        Path subdirectory = Files.createDirectory(directory.resolve("rotating"));
        IOException ioe = assertThrows(IOException.class, () -> LogFileStreams.lines(subdirectory, LogFileFormat.DIRECTORY));
        assertEquals("Unable to read " + subdirectory, ioe.getMessage());
    }

    @Test
    void refusesToStreamAnUnknownFormat() {
        Path path = directory.resolve("gc.log");
        assertThrows(IOException.class, () -> LogFileStreams.lines(path, LogFileFormat.UNKNOWN));
    }

    @Test
    void reportsSourceThatCannotBeRead() {
        Path path = directory.resolve("does-not-exist.log");
        assertThrows(IOException.class, () -> LogFileStreams.lines(path, LogFileFormat.PLAINTEXT));
        assertThrows(IOException.class, () -> LogFileStreams.lines(path, LogFileFormat.ZIP));
        assertThrows(IOException.class, () -> LogFileStreams.lines(path, LogFileFormat.GZIP));
    }

    @Test
    void reportsSourceThatIsNotCompressed() throws IOException {
        Path path = LogSourceFixture.writePlainText(directory, "gc.log");
        assertThrows(IOException.class, () -> LogFileStreams.lines(path, LogFileFormat.GZIP));
    }

    private List<String> lines(Path path, LogFileFormat format) throws IOException {
        try (Stream<String> lines = LogFileStreams.lines(path, format)) {
            return lines.collect(Collectors.toList());
        }
    }
}

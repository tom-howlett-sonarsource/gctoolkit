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

import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeEmpty;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeGzip;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writePlainText;
import static com.microsoft.gctoolkit.logsource.LogSourceFixture.writeZip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourceStreamsTest {

    private static final String FIRST = "[0.001s][info][gc] first";
    private static final String SECOND = "[0.002s][info][gc] second";

    @TempDir
    Path directory;

    @Test
    void streamsAPlainTextLog() throws IOException {
        Path log = writePlainText(directory, "gc.log", FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.lines(log)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.plainTextLines(log)));
    }

    @Test
    void streamsAGZipCompressedLog() throws IOException {
        Path log = writeGzip(directory, "gc.log.gz", FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.lines(log)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.gzipLines(log)));
    }

    @Test
    void streamsTheFirstLogInAZipArchiveSkippingDirectoryEntries() throws IOException {
        Path archive = writeZip(directory, "gc.zip", true, "logs/gc.log", FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.lines(archive)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.zipLines(archive)));
    }

    @Test
    void streamsANamedLogInAZipArchive() throws IOException {
        Path archive = writeZip(directory, "gc.zip", "logs/gc.log", FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceStreams.zipEntryLines(archive, "logs/gc.log")));
    }

    @Test
    void streamingALogThatIsNotInTheArchiveFails() throws IOException {
        Path archive = writeZip(directory, "gc.zip", "logs/gc.log", FIRST);

        IOException failure = assertThrows(IOException.class, () -> LogSourceStreams.zipEntryLines(archive, "logs/other.log"));
        assertTrue(failure.getMessage().contains("logs/other.log"));
    }

    @Test
    void streamingADirectoryFails() {
        IOException failure = assertThrows(IOException.class, () -> LogSourceStreams.lines(directory));
        assertTrue(failure.getMessage().startsWith("Unable to read "));
    }

    @Test
    void anEmptyLogHasNoLines() throws IOException {
        assertEquals(List.of(), collect(LogSourceStreams.lines(writeEmpty(directory, "gc.log"))));
    }

    @Test
    void tailReturnsTheLastLinesOfALog() throws IOException {
        Path log = writePlainText(directory, "gc.log", "one", "two", "three", "four");

        assertEquals(List.of("three", "four"), LogSourceStreams.tail(log, 2));
    }

    @Test
    void tailReturnsEveryLineButTheFirstWhenAskedForMoreLinesThanTheLogHolds() throws IOException {
        Path log = writePlainText(directory, "gc.log", "one", "two", "three");

        assertEquals(List.of("two", "three"), LogSourceStreams.tail(log, 2));
        // The scan back through the log stops on the first byte rather than before it, so a log
        // read to its start gives up the first character of its first line. This is long standing
        // behaviour of the tail read and callers use it to look for the last time stamp in a log.
        assertEquals(List.of("ne", "two", "three"), LogSourceStreams.tail(log, 100));
    }

    @Test
    void tailReadsLogsWrittenWithCarriageReturns() throws IOException {
        Path log = directory.resolve("gc.log");
        Files.write(log, "one\r\ntwo\r\nthree\r\n".getBytes(StandardCharsets.UTF_8));

        // Lines are counted by carriage return but read from the byte that follows it, so the line
        // feed of the first CRLF pair is read as an empty line. Again, long standing behaviour.
        assertEquals(List.of("", "three"), LogSourceStreams.tail(log, 2));
    }

    @Test
    void tailOfAnEmptyLogIsEmpty() throws IOException {
        assertEquals(List.of(), LogSourceStreams.tail(writeEmpty(directory, "gc.log"), 10));
    }

    @Test
    void tailOfASingleLineLogIsEmptyBecauseNoLineEndPrecedesIt() throws IOException {
        Path log = writePlainText(directory, "gc.log", "one");

        assertEquals(List.of(), LogSourceStreams.tail(log, 10));
    }

    @Test
    void tailOfAnUnknownLogFails() {
        assertThrows(IOException.class, () -> LogSourceStreams.tail(directory.resolve("does-not-exist.log"), 10));
    }

    private List<String> collect(Stream<String> lines) {
        try (Stream<String> closeable = lines) {
            return closeable.collect(Collectors.toList());
        }
    }
}

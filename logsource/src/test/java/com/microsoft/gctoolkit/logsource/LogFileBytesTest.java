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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LogFileBytesTest {

    private static final String LOG = "gc.log";

    @TempDir
    Path directory;

    @Test
    public void theSizeOfALogIsReportedInBytes() throws IOException {
        Path path = LogSourceTestFiles.plainText(directory, LOG, List.of("first", "second"));
        assertEquals(Files.size(path), LogFileBytes.sizeInBytes(path));
    }

    @Test
    public void theSizeOfAnEmptyLogIsZero() throws IOException {
        Path path = Files.createFile(directory.resolve(LOG));
        assertEquals(0L, LogFileBytes.sizeInBytes(path));
    }

    @Test
    public void theSizeOfAMissingLogCannotBeRead() {
        assertThrows(IOException.class, () -> LogFileBytes.sizeInBytes(directory.resolve("missing.log")));
    }

    @Test
    public void theRequestedNumberOfTrailingLinesIsRead() throws IOException {
        List<String> lines = numberedLines(200);
        Path path = LogSourceTestFiles.plainText(directory, LOG, lines);

        assertEquals(lines.subList(100, 200), LogFileBytes.tail(path, 100));
    }

    @Test
    public void aLogShorterThanRequestedIsReadToItsEnd() throws IOException {
        List<String> lines = numberedLines(10);
        Path path = LogSourceTestFiles.plainText(directory, LOG, lines);

        List<String> tail = LogFileBytes.tail(path, 100);
        assertEquals("line9", tail.get(tail.size() - 1));
        assertTrue(tail.containsAll(lines.subList(1, 10)), () -> "unexpected tail " + tail);
    }

    @Test
    public void carriageReturnTerminatedLinesAreRead() throws IOException {
        Path path = Files.write(directory.resolve(LOG), "line0\rline1\rline2\r".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("line1", "line2"), LogFileBytes.tail(path, 2));
    }

    @Test
    public void anEmptyLogHasNoTrailingLines() throws IOException {
        Path path = Files.createFile(directory.resolve(LOG));
        assertEquals(List.of(), LogFileBytes.tail(path, 100));
    }

    @Test
    public void aLogWithoutALineTerminatorHasNoTrailingLines() throws IOException {
        Path path = Files.write(directory.resolve(LOG), "single line".getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of(), LogFileBytes.tail(path, 100));
    }

    @Test
    public void theTrailingLinesOfAMissingLogCannotBeRead() {
        assertThrows(IOException.class, () -> LogFileBytes.tail(directory.resolve("missing.log"), 100));
    }

    private List<String> numberedLines(int count) {
        return IntStream.range(0, count).mapToObj(i -> "line" + i).collect(Collectors.toList());
    }
}

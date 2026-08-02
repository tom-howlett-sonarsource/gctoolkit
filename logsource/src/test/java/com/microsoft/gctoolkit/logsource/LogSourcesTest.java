// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSourcesTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");
    private static final String CONTENT = String.join("\n", LINES) + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainTextZipGZipAndDirectory() throws IOException {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(writePlainText()));
        assertEquals(LogSourceFormat.ZIP, LogSources.discover(writeZip("gc.log")));
        assertEquals(LogSourceFormat.GZIP, LogSources.discover(writeGZip()));
        assertEquals(LogSourceFormat.DIRECTORY, LogSources.discover(directory));
    }

    @Test
    void discoversMissingFileAsPlainText() {
        assertEquals(LogSourceFormat.PLAINTEXT, LogSources.discover(directory.resolve("absent.log")));
    }

    @Test
    void magicMatchesOnlyTheFirstTwoBytes() throws IOException {
        Path path = writeGZip();
        assertTrue(LogSources.magic(path, 0x1F, 0x8b));
        assertFalse(LogSources.magic(path, 0x50, 0x4b));
        assertFalse(LogSources.magic(directory.resolve("absent.log"), 0x1F, 0x8b));
    }

    @Test
    void reportsSizeInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, LogSources.sizeInBytes(writePlainText()));
    }

    @Test
    void streamsEachSupportedFormat() throws IOException {
        assertEquals(LINES, collect(writePlainText()));
        assertEquals(LINES, collect(writeGZip()));
        assertEquals(LINES, collect(writeZip("gc.log")));
    }

    @Test
    void streamsFirstFileEntryOfAZipHoldingADirectory() throws IOException {
        assertEquals(LINES, collect(writeZip("logs/gc.log")));
    }

    @Test
    void streamingADirectoryOrAnUnknownFormatFails() {
        IOException directoryFailure = assertThrows(IOException.class, () -> LogSources.stream(directory));
        assertTrue(directoryFailure.getMessage().contains(directory.toString()));
        assertThrows(IOException.class, () -> LogSources.stream(directory, LogSourceFormat.UNKNOWN));
    }

    @Test
    void tailReturnsTheLastLinesOfAPlainTextSource() throws IOException {
        assertEquals(List.of("second line", "third line"), LogSources.tail(writePlainText(), 2));
        // asking for more lines than the source holds walks back past the first line ending, which
        // leaves the read starting one byte into the file. Long standing behaviour, retained as is.
        assertEquals(List.of("irst line", "second line", "third line"), LogSources.tail(writePlainText(), 10));
    }

    @Test
    void tailOfASingleLineWithoutALineEndingIsEmpty() throws IOException {
        Path path = directory.resolve("single.log");
        Files.writeString(path, "only line", StandardCharsets.UTF_8);
        assertEquals(List.of(), LogSources.tail(path, 5));
    }

    @Test
    void tailCollectorRetainsTheLastElements() {
        assertEquals(List.of("8", "9"), Stream.iterate(0, i -> i + 1)
                .limit(10)
                .map(String::valueOf)
                .collect(LogSources.tailCollector(2)));
        assertEquals(List.of("0", "1"), Stream.of("0", "1").collect(LogSources.tailCollector(5)));
    }

    private List<String> collect(Path path) throws IOException {
        try (Stream<String> lines = LogSources.stream(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path writePlainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGZip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String entryName) throws IOException {
        Path path = directory.resolve("gc-" + entryName.replace('/', '-') + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            int separator = entryName.lastIndexOf('/');
            if (separator > 0) {
                output.putNextEntry(new ZipEntry(entryName.substring(0, separator + 1)));
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry(entryName));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

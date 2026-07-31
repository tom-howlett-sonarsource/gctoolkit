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
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final List<String> LINES = List.of("[0.001s][info][gc] first", "[0.002s][info][gc] second");
    private static final String CONTENT = String.join("\n", LINES) + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversTheFormatOfEachKindOfSource() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.discoverFormat(plainText()));
        assertEquals(LogFileFormat.GZIP, GCLogSources.discoverFormat(gzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSources.discoverFormat(zip("gc.log")));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSources.discoverFormat(Files.createDirectory(directory.resolve("logs"))));
    }

    @Test
    void anEmptyFileIsReportedAsPlainText() throws IOException {
        Path empty = Files.createFile(directory.resolve("empty.log"));
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.discoverFormat(empty));
        assertEquals(0L, GCLogSources.sizeInBytes(empty));
    }

    @Test
    void sizesTheSourceInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSources.sizeInBytes(plainText()));
    }

    @Test
    void sizeOfAnUnreadableSourceIsZero() {
        assertEquals(0L, GCLogSources.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    @Test
    void opensPlainZipAndGZipSources() throws IOException {
        assertLinesMatch(LINES, collect(GCLogSources.lines(plainText())));
        assertLinesMatch(LINES, collect(GCLogSources.lines(gzip())));
        assertLinesMatch(LINES, collect(GCLogSources.lines(zip("gc.log"))));
    }

    @Test
    void aSourceThatCannotBeStreamedIsReported() throws IOException {
        Path logs = Files.createDirectory(directory.resolve("logs"));
        IOException ioe = assertThrows(IOException.class, () -> GCLogSources.lines(logs));
        assertTrue(ioe.getMessage().contains(logs.toString()));
        assertThrows(IOException.class, () -> GCLogSources.lines(plainText(), LogFileFormat.UNKNOWN));
    }

    @Test
    void discoversAndOpensTheEntriesOfAZipFile() throws IOException {
        Path archive = zip("first.log", "second.log");
        assertEquals(List.of("first.log", "second.log"), GCLogSources.zipEntryNames(archive));
        assertLinesMatch(LINES, collect(GCLogSources.zipEntryLines(archive, "second.log")));
    }

    @Test
    void readsTheTailOfALogFile() throws IOException {
        assertLinesMatch(LINES.subList(1, 2), GCLogSources.tail(plainText(), 1));
    }

    @Test
    void tailOfAnEmptyLogFileIsEmpty() throws IOException {
        assertTrue(GCLogSources.tail(Files.createFile(directory.resolve("empty.log")), 10).isEmpty());
    }

    private List<String> collect(Stream<String> lines) {
        try (Stream<String> stream = lines) {
            return stream.collect(Collectors.toList());
        }
    }

    private Path plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String... entryNames) throws IOException {
        Path path = directory.resolve("gc-" + entryNames.length + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return path;
    }
}

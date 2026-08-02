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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourcesTest {

    private static final String LINE_ONE = "[0.001s][info][gc] first";
    private static final String LINE_TWO = "[0.002s][info][gc] second";
    private static final String CONTENT = LINE_ONE + "\n" + LINE_TWO + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversFormatOfEachKindOfSource() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.discoverFormat(plainText()));
        assertEquals(LogFileFormat.GZIP, GCLogSources.discoverFormat(gzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSources.discoverFormat(zip()));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSources.discoverFormat(directory));
        assertEquals(LogFileFormat.UNKNOWN, GCLogSources.discoverFormat(null));
    }

    @Test
    void reportsSizeInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSources.sizeInBytes(plainText()));
        assertEquals(0L, GCLogSources.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    @Test
    void opensPlainZipAndGZipSources() throws IOException {
        assertEquals(List.of(LINE_ONE, LINE_TWO), lines(GCLogSources.lines(plainText())));
        assertEquals(List.of(LINE_ONE, LINE_TWO), lines(GCLogSources.lines(gzip())));
        assertEquals(List.of(LINE_ONE, LINE_TWO), lines(GCLogSources.lines(zip())));
        assertEquals(List.of(LINE_ONE, LINE_TWO), lines(GCLogSources.zipEntryLines(zip(), "gc.log")));
    }

    @Test
    void refusesToStreamASourceWithNoKnownFormat() {
        IOException exception = assertThrows(IOException.class,
                () -> GCLogSources.lines(directory, LogFileFormat.DIRECTORY));
        assertTrue(exception.getMessage().startsWith("Unable to read "));
    }

    @Test
    void discoversZipEntriesAndDirectoryContents() throws IOException {
        assertEquals(List.of("gc.log"), GCLogSources.zipEntryNames(zip()));
        assertEquals(List.of(), GCLogSources.zipEntryNames(plainText()));

        plainText();
        Files.writeString(directory.resolve("other.txt"), "ignored\n");
        // gc.zip, gc.log and other.txt
        assertEquals(3, GCLogSources.listFiles(directory).size());

        List<Path> matching = GCLogSources.listFilesStartingWith(directory, "gc.log");
        assertEquals(1, matching.size());
        assertEquals("gc.log", matching.get(0).getFileName().toString());
    }

    @Test
    void tailReadsTheLastLinesOfAFile() throws IOException {
        Path path = directory.resolve("rotating.log");
        Files.writeString(path, "one\ntwo\nthree\nfour\n", StandardCharsets.UTF_8);

        assertEquals(List.of("three", "four"), GCLogSources.tail(path, 2));
        assertEquals(List.of("two", "three", "four"), GCLogSources.tail(path, 3));
        // asking for more lines than the file holds walks back to the start of the file
        assertEquals(4, GCLogSources.tail(path, 100).size());
        assertEquals(List.of(), GCLogSources.tail(plainTextWithoutLineEndings(), 10));
    }

    private List<String> lines(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path plainTextWithoutLineEndings() throws IOException {
        Path path = directory.resolve("single-line.log");
        Files.writeString(path, LINE_ONE, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip() throws IOException {
        Path path = directory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

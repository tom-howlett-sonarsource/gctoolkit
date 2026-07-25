// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final List<String> LINES = List.of("first line", "second line", "third line");

    @TempDir
    Path directory;

    @Test
    void discoverFormatRecognizesPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(plainText("gc.log", LINES)));
    }

    @Test
    void discoverFormatRecognizesGZip() throws IOException {
        assertEquals(LogFileFormat.GZIP, GCLogSource.discoverFormat(gzip("gc.log.gz", LINES)));
    }

    @Test
    void discoverFormatRecognizesZip() throws IOException {
        assertEquals(LogFileFormat.ZIP, GCLogSource.discoverFormat(zip("gc.log.zip", "gc.log", LINES)));
    }

    @Test
    void discoverFormatRecognizesDirectory() {
        assertEquals(LogFileFormat.DIRECTORY, GCLogSource.discoverFormat(directory));
    }

    @Test
    void discoverFormatTreatsFilesTooShortToHoldAMagicNumberAsPlainText() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(plainText("empty.log", List.of())));
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(bytes("single.log", (byte) 0x1F)));
    }

    @Test
    void discoverFormatTreatsMissingFilesAsPlainText() {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(directory.resolve("absent.log")));
    }

    @Test
    void sizeInBytesReportsTheLengthOfTheSource() throws IOException {
        assertEquals(3L, GCLogSource.sizeInBytes(bytes("three.log", (byte) 1, (byte) 2, (byte) 3)));
        assertEquals(0L, GCLogSource.sizeInBytes(plainText("empty.log", List.of())));
    }

    @Test
    void sizeInBytesIsZeroWhenThereIsNoRegularFileToMeasure() {
        assertEquals(0L, GCLogSource.sizeInBytes(directory));
        assertEquals(0L, GCLogSource.sizeInBytes(directory.resolve("absent.log")));
    }

    @Test
    void linesReadsPlainTextSources() throws IOException {
        assertEquals(LINES, readLines(plainText("gc.log", LINES)));
    }

    @Test
    void linesReadsGZipSources() throws IOException {
        assertEquals(LINES, readLines(gzip("gc.log.gz", LINES)));
    }

    @Test
    void linesReadsTheFirstFileInAZipSource() throws IOException {
        assertEquals(LINES, readLines(zip("gc.log.zip", "gc.log", LINES)));
    }

    @Test
    void linesSkipsLeadingDirectoryEntriesInAZipSource() throws IOException {
        assertEquals(LINES, readLines(zip("gc.log.zip", "logs/gc.log", LINES, "logs/")));
    }

    @Test
    void linesRejectsSourcesThatCannotBeRead() {
        IOException ioe = assertThrows(IOException.class, () -> GCLogSource.lines(directory));
        assertEquals("Unable to read " + directory, ioe.getMessage());
    }

    @Test
    void zipEntryLinesReadsTheNamedEntry() throws IOException {
        Path path = zip("rotating.zip", "gc.log.0", LINES, "gc.log.1");
        try (Stream<String> lines = GCLogSource.zipEntryLines(path, "gc.log.0")) {
            assertEquals(LINES, lines.collect(Collectors.toList()));
        }
    }

    @Test
    void zipEntryLinesRejectsAnUnknownEntry() throws IOException {
        Path path = zip("rotating.zip", "gc.log.0", LINES);
        assertThrows(IOException.class, () -> GCLogSource.zipEntryLines(path, "absent.log"));
    }

    @Test
    void tailReturnsTheRequestedNumberOfTrailingLines() throws IOException {
        Path path = plainText("gc.log", List.of("one", "two", "three", "four", "five"));
        assertEquals(List.of("four", "five"), GCLogSource.tail(path, 2));
    }

    @Test
    void tailReturnsTrailingLinesOfASourceWithoutATrailingLineFeed() throws IOException {
        Path path = write("gc.log", "one\ntwo\nthree\nfour");
        assertEquals(List.of("three", "four"), GCLogSource.tail(path, 2));
    }

    @Test
    void tailReturnsTrailingLinesOfASourceWithWindowsLineEndings() throws IOException {
        Path path = write("gc.log", "one\r\ntwo\r\nthree\r\nfour\r\n");
        List<String> tail = GCLogSource.tail(path, 3);
        assertEquals("four", tail.get(tail.size() - 1));
        assertTrue(tail.contains("three"), "tail is missing the penultimate line: " + tail);
    }

    @Test
    void tailStopsAtTheStartOfTheSource() throws IOException {
        List<String> tail = GCLogSource.tail(plainText("gc.log", LINES), 100);
        assertTrue(tail.size() <= LINES.size(), "tail returned more lines than the source holds: " + tail);
        assertEquals(LINES.get(LINES.size() - 1), tail.get(tail.size() - 1));
        assertTrue(tail.contains(LINES.get(1)), "tail is missing the penultimate line: " + tail);
    }

    @Test
    void tailReturnsNothingWhenThereIsNothingToRead() throws IOException {
        assertEquals(List.of(), GCLogSource.tail(plainText("empty.log", List.of()), 10));
        assertEquals(List.of(), GCLogSource.tail(bytes("single.log", (byte) 'a'), 10));
        assertEquals(List.of(), GCLogSource.tail(plainText("gc.log", LINES), 0));
    }

    @Test
    void tailRejectsASourceItCannotOpen() {
        assertThrows(IOException.class, () -> GCLogSource.tail(directory, 10));
        assertThrows(IOException.class, () -> GCLogSource.tail(directory.resolve("absent.log"), 10));
    }

    private List<String> readLines(Path path) throws IOException {
        try (Stream<String> lines = GCLogSource.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plainText(String name, List<String> lines) throws IOException {
        return Files.write(directory.resolve(name), lines, UTF_8);
    }

    private Path write(String name, String content) throws IOException {
        return Files.write(directory.resolve(name), content.getBytes(UTF_8));
    }

    private Path bytes(String name, byte... content) throws IOException {
        return Files.write(directory.resolve(name), content);
    }

    private Path gzip(String name, List<String> lines) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(joinLines(lines));
        }
        return path;
    }

    private Path zip(String name, String entryName, List<String> lines, String... otherEntryNames) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String otherEntryName : otherEntryNames) {
                out.putNextEntry(new ZipEntry(otherEntryName));
                if (!otherEntryName.endsWith("/"))
                    out.write(joinLines(List.of("ignored")));
                out.closeEntry();
            }
            out.putNextEntry(new ZipEntry(entryName));
            out.write(joinLines(lines));
            out.closeEntry();
        }
        return path;
    }

    private byte[] joinLines(List<String> lines) {
        return lines.stream().collect(Collectors.joining("\n", "", "\n")).getBytes(UTF_8);
    }
}

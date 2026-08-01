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
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String LOG_CONTENT = "first line\nsecond line\nthird line\n";
    private static final List<String> LOG_LINES = List.of("first line", "second line", "third line");

    @TempDir
    Path directory;

    @Test
    void discoversTheFormatOfEachKindOfSource() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(plainText()));
        assertEquals(LogFileFormat.GZIP, GCLogSource.discoverFormat(gzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSource.discoverFormat(zip("gc.log")));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSource.discoverFormat(directory));
    }

    @Test
    void reportsUnreadableSourcesAsPlainTextAndMissingPathsAsUnknown() {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSource.discoverFormat(directory.resolve("does-not-exist.log")));
        assertEquals(LogFileFormat.UNKNOWN, GCLogSource.discoverFormat(null));
    }

    @Test
    void reportsTheSizeOfASourceInBytes() throws IOException {
        Path path = plainText();
        assertEquals(LOG_CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSource.sizeInBytes(path));
        assertThrows(IOException.class, () -> GCLogSource.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    @Test
    void streamsPlainTextZipAndGZipSources() throws IOException {
        assertLines(LOG_LINES, GCLogSource.stream(plainText()));
        assertLines(LOG_LINES, GCLogSource.stream(zip("gc.log")));
        assertLines(LOG_LINES, GCLogSource.stream(gzip()));
    }

    @Test
    void streamsEachFormatDirectly() throws IOException {
        assertLines(LOG_LINES, GCLogSource.streamPlainText(plainText()));
        assertLines(LOG_LINES, GCLogSource.streamZip(zip("gc.log")));
        assertLines(LOG_LINES, GCLogSource.streamGZip(gzip()));
    }

    @Test
    void streamsTheFirstLogFileHeldInAZip() throws IOException {
        Path path = directory.resolve("segments.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
            writeEntry(zip, "logs/gc.log.0", LOG_CONTENT);
            writeEntry(zip, "logs/gc.log.1", "fourth line\n");
        }

        assertLines(LOG_LINES, GCLogSource.streamZip(path));
        assertEquals(List.of("logs/gc.log.0", "logs/gc.log.1"), GCLogSource.zipEntryNames(path));
        assertLines(List.of("fourth line"), GCLogSource.streamZipEntry(path, "logs/gc.log.1"));
        assertThrows(IOException.class, () -> GCLogSource.streamZipEntry(path, "logs/missing.log"));
    }

    @Test
    void streamsAnEmptyZipAsAnEmptyStream() throws IOException {
        Path path = directory.resolve("empty.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            zip.putNextEntry(new ZipEntry("logs/"));
            zip.closeEntry();
        }

        assertLines(List.of(), GCLogSource.streamZip(path));
        assertTrue(GCLogSource.zipEntryNames(path).isEmpty());
    }

    @Test
    void refusesToStreamSourcesThatCannotBeRead() throws IOException {
        IOException directoryFailure = assertThrows(IOException.class, () -> GCLogSource.stream(directory));
        assertTrue(directoryFailure.getMessage().contains(directory.toString()));
        assertThrows(IOException.class, () -> GCLogSource.stream(plainText(), LogFileFormat.UNKNOWN));
    }

    @Test
    void readsTheLastLinesOfASource() throws IOException {
        Path path = plainText();
        assertLinesMatch(List.of("second line", "third line"), GCLogSource.tail(path, 2));
    }

    @Test
    void readsFromTheSecondByteWhenAskedForMoreLinesThanTheSourceHolds() throws IOException {
        // the scan runs off the front of the file, leaving the read position on the second byte.
        assertLinesMatch(List.of("irst line", "second line", "third line"), GCLogSource.tail(plainText(), 100));
    }

    @Test
    void readsTheLastLinesOfASourceWithCarriageReturnLineEndings() throws IOException {
        Path path = directory.resolve("carriage-return.log");
        Files.writeString(path, "one\rtwo\rthree\r", StandardCharsets.UTF_8);
        assertLinesMatch(List.of("two", "three"), GCLogSource.tail(path, 2));
    }

    @Test
    void refusesToStreamACorruptZip() throws IOException {
        byte[] corrupt = Files.readAllBytes(zip("gc.log"));
        // the compression method is held in bytes 8 and 9 of the local file header
        corrupt[8] = (byte) 99;
        corrupt[9] = 0;
        Path path = directory.resolve("corrupt.zip");
        Files.write(path, corrupt);

        assertEquals(LogFileFormat.ZIP, GCLogSource.discoverFormat(path));
        assertThrows(IOException.class, () -> GCLogSource.streamZip(path));
    }

    @Test
    void readsNoLinesFromASourceWithoutALineEnding() throws IOException {
        Path path = directory.resolve("one-line.log");
        Files.writeString(path, "only line", StandardCharsets.UTF_8);
        assertTrue(GCLogSource.tail(path, 10).isEmpty());
    }

    private void assertLines(List<String> expected, Stream<String> stream) {
        try (Stream<String> lines = stream) {
            assertEquals(expected, lines.collect(Collectors.toList()));
        }
    }

    private Path plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, LOG_CONTENT, StandardCharsets.UTF_8);
        assertFalse(Files.isDirectory(path));
        return path;
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG_CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String entryName) throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            writeEntry(zip, entryName, LOG_CONTENT);
        }
        return path;
    }

    private void writeEntry(ZipOutputStream zip, String entryName, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

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

class LogSourceTest {

    private static final String FIRST_LINE = "[0.001s][info][gc] first";
    private static final String SECOND_LINE = "[0.002s][info][gc] second";
    private static final String LOG = FIRST_LINE + "\n" + SECOND_LINE + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainTextSource() throws IOException {
        LogSource source = new LogSource(plainText("gc.log", LOG));
        assertEquals(LogSourceFormat.PLAINTEXT, source.getFormat());
        assertTrue(source.isPlainText());
        assertFalse(source.isZip());
        assertFalse(source.isGZip());
        assertFalse(source.isDirectory());
        assertEquals(LOG.length(), source.sizeInBytes());
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), collect(source));
    }

    @Test
    void discoversZipSource() throws IOException {
        LogSource source = new LogSource(zip("gc.log.zip"));
        assertEquals(LogSourceFormat.ZIP, source.getFormat());
        assertTrue(source.isZip());
        assertFalse(source.isPlainText());
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), collect(source));
    }

    @Test
    void discoversGZipSource() throws IOException {
        LogSource source = new LogSource(gzip("gc.log.gz"));
        assertEquals(LogSourceFormat.GZIP, source.getFormat());
        assertTrue(source.isGZip());
        assertFalse(source.isPlainText());
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), collect(source));
    }

    @Test
    void discoversDirectorySource() {
        LogSource source = new LogSource(directory);
        assertEquals(LogSourceFormat.DIRECTORY, source.getFormat());
        assertTrue(source.isDirectory());
        assertEquals(directory.toString(), source.toString());
        IOException failure = assertThrows(IOException.class, source::lines);
        assertEquals("Unable to read " + directory, failure.getMessage());
    }

    @Test
    void treatsSourceThatCannotBeReadAsPlainText() {
        Path missing = directory.resolve("missing.log");
        LogSource source = new LogSource(missing);
        assertEquals(LogSourceFormat.PLAINTEXT, source.getFormat());
        assertEquals(missing, source.getPath());
        assertThrows(IOException.class, source::sizeInBytes);
        assertThrows(IOException.class, source::lines);
    }

    @Test
    void skipsDirectoryEntriesWhenStreamingZipSource() throws IOException {
        Path path = directory.resolve("directory.zip");
        try (ZipOutputStream zipStream = new ZipOutputStream(Files.newOutputStream(path))) {
            zipStream.putNextEntry(new ZipEntry("logs/"));
            zipStream.closeEntry();
            zipStream.putNextEntry(new ZipEntry("logs/gc.log"));
            zipStream.write(LOG.getBytes(StandardCharsets.UTF_8));
            zipStream.closeEntry();
        }
        LogSource source = new LogSource(path);
        assertEquals(List.of("logs/gc.log"), source.zipEntryNames());
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), collect(source));
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        LogSource source = new LogSource(zip("rotating.zip", "gc.log.0", "gc.log.1"));
        assertEquals(List.of("gc.log.0", "gc.log.1"), source.zipEntryNames());
        try (Stream<String> lines = source.zipEntryLines("gc.log.1")) {
            assertEquals(List.of(FIRST_LINE, SECOND_LINE), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void reportsMissingZipEntry() throws IOException {
        LogSource source = new LogSource(zip("gc.log.zip"));
        IOException failure = assertThrows(IOException.class, () -> source.zipEntryLines("absent.log"));
        assertTrue(failure.getMessage().startsWith("Unable to find absent.log in "));
    }

    @Test
    void reportsEntryNamesOnlyForZipSources() throws IOException {
        LogSource source = new LogSource(plainText("gc.log", LOG));
        assertThrows(IOException.class, source::zipEntryNames);
    }

    @Test
    void readsLastLinesOfSource() throws IOException {
        LogSource source = new LogSource(plainText("gc.log", LOG));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), source.tail(10));
        assertEquals(List.of(SECOND_LINE), source.tail(1));
    }

    @Test
    void readsLastLinesOfSourceWithCarriageReturns() throws IOException {
        LogSource source = new LogSource(plainText("gc.log", FIRST_LINE + "\r\n" + SECOND_LINE + "\r\n"));
        assertEquals(List.of(SECOND_LINE), source.tail(1));
        assertEquals(List.of(FIRST_LINE, SECOND_LINE), source.tail(2));
    }

    @Test
    void readsFinalLineThatIsNotTerminated() throws IOException {
        LogSource source = new LogSource(plainText("gc.log", FIRST_LINE + "\n" + SECOND_LINE));
        assertEquals(List.of(SECOND_LINE), source.tail(1));
    }

    @Test
    void readsNoLinesFromAnEmptySource() throws IOException {
        LogSource source = new LogSource(plainText("gc.log", ""));
        assertTrue(source.tail(10).isEmpty());
    }

    @Test
    void readsOnlyPlainTextSourcesFromTheEnd() throws IOException {
        LogSource source = new LogSource(gzip("gc.log.gz"));
        IOException failure = assertThrows(IOException.class, () -> source.tail(10));
        assertTrue(failure.getMessage().startsWith("Unable to read the last lines of a GZIP source: "));
    }

    private List<String> collect(LogSource source) throws IOException {
        try (Stream<String> lines = source.lines()) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plainText(String fileName, String content) throws IOException {
        Path path = directory.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip(String fileName) throws IOException {
        Path path = directory.resolve(fileName);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(LOG.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String fileName, String... entryNames) throws IOException {
        String[] entries = (entryNames.length == 0) ? new String[]{"gc.log"} : entryNames;
        Path path = directory.resolve(fileName);
        try (ZipOutputStream zipStream = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entryName : entries) {
                zipStream.putNextEntry(new ZipEntry(entryName));
                zipStream.write(LOG.getBytes(StandardCharsets.UTF_8));
                zipStream.closeEntry();
            }
        }
        return path;
    }
}

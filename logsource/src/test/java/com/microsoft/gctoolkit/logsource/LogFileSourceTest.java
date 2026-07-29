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

class LogFileSourceTest {

    private static final String LINE_ONE = "[0.001s][info][gc] first";
    private static final String LINE_TWO = "[0.002s][info][gc] second";
    private static final String CONTENT = LINE_ONE + "\n" + LINE_TWO + "\n";

    @TempDir
    Path directory;

    @Test
    void discoversPlainTextSource() throws IOException {
        LogFileSource source = new LogFileSource(writePlainText());
        assertEquals(LogFileFormat.PLAINTEXT, source.getFormat());
        assertTrue(source.isPlainText());
        assertFalse(source.isZip());
        assertFalse(source.isGZip());
        assertFalse(source.isDirectory());
    }

    @Test
    void discoversZipSource() throws IOException {
        LogFileSource source = new LogFileSource(writeZip());
        assertEquals(LogFileFormat.ZIP, source.getFormat());
        assertTrue(source.isZip());
        assertFalse(source.isPlainText());
    }

    @Test
    void discoversGZipSource() throws IOException {
        LogFileSource source = new LogFileSource(writeGZip());
        assertEquals(LogFileFormat.GZIP, source.getFormat());
        assertTrue(source.isGZip());
    }

    @Test
    void discoversDirectorySource() {
        LogFileSource source = new LogFileSource(directory);
        assertEquals(LogFileFormat.DIRECTORY, source.getFormat());
        assertTrue(source.isDirectory());
    }

    @Test
    void treatsUnreadableSourceAsPlainText() {
        LogFileSource source = new LogFileSource(directory.resolve("does-not-exist.log"));
        assertEquals(LogFileFormat.PLAINTEXT, source.getFormat());
    }

    @Test
    void reportsPathAndSizeInBytes() throws IOException {
        Path path = writePlainText();
        LogFileSource source = new LogFileSource(path);
        assertEquals(path, source.getPath());
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, source.sizeInBytes());
    }

    @Test
    void sizeOfMissingSourceIsReportedAsAnIOException() {
        LogFileSource source = new LogFileSource(directory.resolve("does-not-exist.log"));
        assertThrows(IOException.class, source::sizeInBytes);
    }

    @Test
    void streamsPlainTextSource() throws IOException {
        assertStreamsBothLines(writePlainText());
    }

    @Test
    void streamsZipSource() throws IOException {
        assertStreamsBothLines(writeZip());
    }

    @Test
    void streamsGZipSource() throws IOException {
        assertStreamsBothLines(writeGZip());
    }

    @Test
    void streamOfADirectorySourceIsRejected() {
        LogFileSource source = new LogFileSource(directory);
        IOException ioe = assertThrows(IOException.class, source::stream);
        assertTrue(ioe.getMessage().contains(directory.toString()));
    }

    @Test
    void tailReturnsTheLastLinesOfTheSource() throws IOException {
        Path path = directory.resolve("many.log");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < 50; index++)
            builder.append("line ").append(index).append('\n');
        Files.writeString(path, builder.toString(), StandardCharsets.UTF_8);

        List<String> tail = new LogFileSource(path).tail(10);
        assertEquals(10, tail.size());
        assertEquals("line 40", tail.get(0));
        assertEquals("line 49", tail.get(9));
    }

    @Test
    void tailOfASourceWithFewerLinesThanRequestedReturnsWhatIsAvailable() throws IOException {
        List<String> tail = new LogFileSource(writePlainText()).tail(10);
        assertEquals(List.of(LINE_ONE, LINE_TWO), tail);
    }

    @Test
    void tailReadsCarriageReturnLineFeedTerminatedSources() throws IOException {
        Path path = write("windows.log", "alpha\r\nbeta\r\ngamma\r\n");
        assertEquals(List.of("beta", "gamma"), new LogFileSource(path).tail(2));
    }

    @Test
    void tailReadsCarriageReturnTerminatedSources() throws IOException {
        Path path = write("classic.log", "alpha\rbeta\rgamma\r");
        assertEquals(List.of("beta", "gamma"), new LogFileSource(path).tail(2));
    }

    @Test
    void tailReadsASourceThatDoesNotEndWithALineTerminator() throws IOException {
        Path path = write("unterminated.log", "alpha\nbeta");
        assertEquals(List.of("beta"), new LogFileSource(path).tail(1));
    }

    @Test
    void tailOfAnEmptySourceIsEmpty() throws IOException {
        Path path = write("empty.log", "");
        assertEquals(List.of(), new LogFileSource(path).tail(10));
    }

    @Test
    void tailOfNoLinesIsEmpty() throws IOException {
        assertEquals(List.of(), new LogFileSource(writePlainText()).tail(0));
    }

    private void assertStreamsBothLines(Path path) throws IOException {
        try (Stream<String> lines = new LogFileSource(path).stream()) {
            assertEquals(List.of(LINE_ONE, LINE_TWO), lines.collect(Collectors.toList()));
        }
    }

    private Path writePlainText() throws IOException {
        return write("gc.log", CONTENT);
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = directory.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGZip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    /**
     * The archive leads with a directory entry so that the reader has to skip past it to find the log.
     */
    private Path writeZip() throws IOException {
        Path path = directory.resolve("gc.log.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry("logs/gc.log"));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

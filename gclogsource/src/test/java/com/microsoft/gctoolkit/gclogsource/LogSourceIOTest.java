// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

class LogSourceIOTest {

    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final int LARGE_LINE_LENGTH = (1024 * 1024) + 1;

    @TempDir
    private Path tempDir;

    @Test
    void detectsSupportedFormatsByContent() throws IOException {
        Path plainText = write("gc.log", FIRST);
        Path gzip = writeGzip("gc.log.gz", FIRST);
        Path zip = writeZip("gc.zip", List.of(new ArchiveEntry("gc.log", FIRST)));
        Path directory = Files.createDirectory(tempDir.resolve("logs"));

        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceIO.detectFormat(plainText));
        assertEquals(LogSourceFormat.GZIP, LogSourceIO.detectFormat(gzip));
        assertEquals(LogSourceFormat.ZIP, LogSourceIO.detectFormat(zip));
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceIO.detectFormat(directory));
    }

    @Test
    void reportsMissingSources() {
        Path missing = tempDir.resolve("missing.log");

        assertThrows(IOException.class, () -> LogSourceIO.detectFormat(missing));
    }

    @Test
    void discoversAndCountsDirectoryEntries() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("logs"));
        Path first = Files.createFile(directory.resolve("gc.log.0"));
        Path second = Files.createFile(directory.resolve("gc.log.1"));

        assertEquals(List.of(first, second), LogSourceIO.list(directory).stream().sorted().collect(Collectors.toList()));
        assertEquals(2, LogSourceIO.numberOfFiles(LogSourceFormat.DIRECTORY, directory));
    }

    @Test
    void discoversAndCountsOnlyFileEntriesInZip() throws IOException {
        Path zip = writeZip("gc.zip", List.of(
                new ArchiveEntry("logs/", null),
                new ArchiveEntry("logs/gc.log.0", FIRST),
                new ArchiveEntry("logs/gc.log.1", SECOND)));

        assertEquals(List.of("logs/gc.log.0", "logs/gc.log.1"), LogSourceIO.zipEntryNames(zip));
        assertEquals(2, LogSourceIO.numberOfFiles(LogSourceFormat.ZIP, zip));
    }

    @Test
    void countsSingleFileSources() throws IOException {
        Path plainText = write("gc.log", FIRST);
        Path gzip = writeGzip("gc.log.gz", FIRST);

        assertEquals(1, LogSourceIO.numberOfFiles(LogSourceFormat.PLAINTEXT, plainText));
        assertEquals(1, LogSourceIO.numberOfFiles(LogSourceFormat.GZIP, gzip));
        assertEquals(0, LogSourceIO.numberOfFiles(LogSourceFormat.UNKNOWN, plainText));
    }

    @Test
    void streamsPlainTextAndCompressedSources() throws IOException {
        String content = FIRST + "\n" + SECOND + "\n";
        Path plainText = write("gc.log", content);
        Path gzip = writeGzip("gc.log.gz", content);
        Path zip = writeZip("gc.zip", List.of(
                new ArchiveEntry("logs/", null),
                new ArchiveEntry("gc.log", content)));

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.PLAINTEXT, plainText)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.GZIP, gzip)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(LogSourceFormat.ZIP, zip)));
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        Path zip = writeZip("gc.zip", List.of(
                new ArchiveEntry("first.log", FIRST),
                new ArchiveEntry("second.log", SECOND)));

        assertEquals(List.of(SECOND), collect(LogSourceIO.streamZipEntry(zip, "second.log")));
        assertEquals(List.of(), collect(LogSourceIO.streamZipEntry(zip, "missing.log")));
    }

    @Test
    void returnsEmptyStreamForZipWithoutFileEntries() throws IOException {
        Path zip = writeZip("empty.zip", List.of(new ArchiveEntry("logs/", null)));

        assertEquals(List.of(), collect(LogSourceIO.stream(LogSourceFormat.ZIP, zip)));
    }

    @Test
    void rejectsStreamingNonFileFormats() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("logs"));

        assertThrows(IOException.class, () -> LogSourceIO.stream(LogSourceFormat.DIRECTORY, directory));
    }

    @Test
    void rejectsMalformedGzipSource() throws IOException {
        Path gzip = tempDir.resolve("malformed.gz");
        Files.write(gzip, new byte[]{0x1F, (byte) 0x8B});

        assertEquals(LogSourceFormat.GZIP, LogSourceIO.detectFormat(gzip));
        assertThrows(IOException.class, () -> LogSourceIO.stream(LogSourceFormat.GZIP, gzip));
    }

    @Test
    void readsLastLinesWithCommonLineEndings() throws IOException {
        Path lineFeed = write("lf.log", "one\ntwo\nthree\n");
        Path carriageReturn = write("crlf.log", "one\r\ntwo\r\nthree\r\n");

        assertEquals(List.of("two", "three"), LogSourceIO.tail(lineFeed, 2));
        assertEquals(List.of("two", "three"), LogSourceIO.tail(carriageReturn, 2));
        assertEquals(List.of(), LogSourceIO.tail(lineFeed, 0));
        assertThrows(IllegalArgumentException.class, () -> LogSourceIO.tail(lineFeed, -1));
    }

    @Test
    void boundsTailReadsForLargeLeadingLine() throws IOException {
        Path log = write("large.log", "a".repeat(LARGE_LINE_LENGTH) + "\nlast\n");

        assertEquals(List.of("last"), LogSourceIO.tail(log, 2));
    }

    @Test
    void doesNotLoadLargeFileWithoutLineEndingsIntoTail() throws IOException {
        Path log = write("large-single-line.log", "a".repeat(LARGE_LINE_LENGTH));

        assertEquals(List.of(), LogSourceIO.tail(log, 2));
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeGzip(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String fileName, List<ArchiveEntry> entries) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (ArchiveEntry archiveEntry : entries) {
                output.putNextEntry(new ZipEntry(archiveEntry.name));
                if (archiveEntry.content != null) {
                    output.write(archiveEntry.content.getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }

    private static final class ArchiveEntry {
        private final String name;
        private final String content;

        private ArchiveEntry(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }
}

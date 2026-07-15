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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogSourceIOTest {

    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String THIRD = "third";
    private static final String FOURTH = "fourth";
    private static final String PLAIN_LOG = "plain.log";
    private static final String GC_ZIP = "gc.zip";
    private static final String GC_LOG = "gc.log";
    private static final String SECOND_LOG = "second.log";

    @TempDir
    private Path tempDirectory;

    @Test
    void detectsSupportedFormatsFromContent() throws IOException {
        Path plainText = writePlainText(PLAIN_LOG, FIRST);
        Path directory = Files.createDirectory(tempDirectory.resolve("logs"));
        Path gzip = writeGZip("gc.log.gz", FIRST);
        Path zip = writeZip(GC_ZIP, GC_LOG, FIRST);

        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceIO.detectFormat(plainText));
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceIO.detectFormat(directory));
        assertEquals(LogSourceFormat.GZIP, LogSourceIO.detectFormat(gzip));
        assertEquals(LogSourceFormat.ZIP, LogSourceIO.detectFormat(zip));
    }

    @Test
    void discoversAndCountsDirectoryFiles() throws IOException {
        Path directory = Files.createDirectory(tempDirectory.resolve("logs"));
        Path first = Files.createFile(directory.resolve("first.log"));
        Path second = Files.createFile(directory.resolve(SECOND_LOG));
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(List.of(first, second), LogSourceIO.list(directory).stream().sorted().collect(Collectors.toList()));
        assertEquals(2, LogSourceIO.countFiles(directory, LogSourceFormat.DIRECTORY));
    }

    @Test
    void discoversAndCountsZipEntries() throws IOException {
        Path zip = tempDirectory.resolve(GC_ZIP);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            writeZipEntry(output, "nested/first.log", FIRST);
            writeZipEntry(output, SECOND_LOG, SECOND);
            writeZipEntry(output, "__MACOSX/._second.log", "metadata");
            writeZipEntry(output, ".DS_Store", "metadata");
        }

        assertEquals(List.of("nested/first.log", SECOND_LOG), LogSourceIO.zipEntryNames(zip));
        assertEquals(2, LogSourceIO.countFiles(zip, LogSourceFormat.ZIP));
    }

    @Test
    void streamsPlainTextGZipAndFirstZipEntry() throws IOException {
        Path plainText = writePlainText(PLAIN_LOG, FIRST, SECOND);
        Path gzip = writeGZip("gc.log.gz", FIRST, SECOND);
        Path zip = writeZip(GC_ZIP, GC_LOG, FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(plainText, LogSourceFormat.PLAINTEXT)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(gzip, LogSourceFormat.GZIP)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(zip, LogSourceFormat.ZIP)));
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        Path zip = tempDirectory.resolve(GC_ZIP);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeZipEntry(output, "first.log", FIRST);
            writeZipEntry(output, SECOND_LOG, SECOND);
        }

        assertEquals(List.of(SECOND), collect(LogSourceIO.streamZipEntry(zip, SECOND_LOG)));
    }

    @Test
    void countsPlainTextAndRejectsDirectoryStreaming() throws IOException {
        Path plainText = writePlainText(PLAIN_LOG, FIRST);
        Path directory = Files.createDirectory(tempDirectory.resolve("logs"));

        assertEquals(1, LogSourceIO.countFiles(plainText, LogSourceFormat.PLAINTEXT));
        assertThrows(IOException.class, () -> LogSourceIO.stream(directory, LogSourceFormat.DIRECTORY));
    }

    @Test
    void rejectsUnreadableZipSources() throws IOException {
        Path emptyZip = tempDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Empty archive.
        }
        Path zip = writeZip(GC_ZIP, GC_LOG, FIRST);

        assertThrows(IOException.class, () -> LogSourceIO.stream(emptyZip, LogSourceFormat.ZIP));
        assertThrows(IOException.class, () -> LogSourceIO.streamZipEntry(zip, "missing.log"));
    }

    @Test
    void rejectsDirectoryZipEntryAndInvalidGZip() throws IOException {
        Path zip = tempDirectory.resolve("directory.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("directory/"));
            output.closeEntry();
        }
        Path invalidGzip = writePlainText("invalid.gz", "not gzip content");

        assertThrows(IOException.class, () -> LogSourceIO.streamZipEntry(zip, "directory/"));
        assertThrows(IOException.class, () -> LogSourceIO.stream(invalidGzip, LogSourceFormat.GZIP));
    }

    @Test
    void readsBoundedTailAcrossLineEndings() throws IOException {
        Path lineFeed = writePlainText("lf.log", FIRST, SECOND, THIRD, FOURTH);
        Path carriageReturnLineFeed = tempDirectory.resolve("crlf.log");
        Files.writeString(carriageReturnLineFeed, String.join("\r\n", FIRST, SECOND, THIRD) + "\r\n");
        Path carriageReturn = tempDirectory.resolve("cr.log");
        Files.writeString(carriageReturn, String.join("\r", FIRST, SECOND, THIRD) + "\r");

        assertEquals(List.of(THIRD, FOURTH), LogSourceIO.tail(lineFeed, 2));
        assertEquals(List.of(SECOND, THIRD), LogSourceIO.tail(carriageReturnLineFeed, 2));
        assertEquals(List.of(SECOND, THIRD), LogSourceIO.tail(carriageReturn, 2));
        assertEquals(List.of(FIRST, SECOND, THIRD, FOURTH), LogSourceIO.tail(lineFeed, 10));
        assertEquals(List.of(), LogSourceIO.tail(lineFeed, 0));
        assertThrows(IllegalArgumentException.class, () -> LogSourceIO.tail(lineFeed, -1));
    }

    @Test
    void collectsBoundedTail() {
        List<String> tail = Stream.of(FIRST, SECOND, THIRD, FOURTH)
                .collect(LogSourceIO.tailCollector(2));

        assertEquals(List.of(THIRD, FOURTH), tail);
        assertEquals(List.of(), Stream.of(FIRST).collect(LogSourceIO.tailCollector(0)));
        assertThrows(IllegalArgumentException.class, () -> LogSourceIO.tailCollector(-1));
    }

    @Test
    void combinesBoundedTailFromParallelStream() {
        List<Integer> tail = IntStream.range(0, 100)
                .boxed()
                .parallel()
                .collect(LogSourceIO.tailCollector(3));

        assertEquals(List.of(97, 98, 99), tail);
    }

    private Path writePlainText(String fileName, String... lines) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        return Files.write(path, List.of(lines));
    }

    private Path writeGZip(String fileName, String... lines) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path writeZip(String fileName, String entryName, String... lines) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(output, entryName, lines);
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream output, String entryName, String... lines) throws IOException {
        output.putNextEntry(new ZipEntry(entryName));
        output.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gclogsource.LogSourceFormat;
import com.microsoft.gctoolkit.gclogsource.LogSourceIO;
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

    @TempDir
    private Path tempDirectory;

    @Test
    void detectsSupportedFormatsFromContent() throws IOException {
        Path plainText = writePlainText("plain.log", FIRST);
        Path directory = Files.createDirectory(tempDirectory.resolve("logs"));
        Path gzip = writeGZip("gc.log.gz", FIRST);
        Path zip = writeZip("gc.zip", "gc.log", FIRST);

        assertEquals(LogSourceFormat.PLAINTEXT, LogSourceIO.detectFormat(plainText));
        assertEquals(LogSourceFormat.DIRECTORY, LogSourceIO.detectFormat(directory));
        assertEquals(LogSourceFormat.GZIP, LogSourceIO.detectFormat(gzip));
        assertEquals(LogSourceFormat.ZIP, LogSourceIO.detectFormat(zip));
    }

    @Test
    void discoversAndCountsDirectoryFiles() throws IOException {
        Path directory = Files.createDirectory(tempDirectory.resolve("logs"));
        Path first = Files.createFile(directory.resolve("first.log"));
        Path second = Files.createFile(directory.resolve("second.log"));
        Files.createDirectory(directory.resolve("nested"));

        assertEquals(List.of(first, second), LogSourceIO.list(directory).stream().sorted().collect(Collectors.toList()));
        assertEquals(2, LogSourceIO.countFiles(directory, LogSourceFormat.DIRECTORY));
    }

    @Test
    void discoversAndCountsZipEntries() throws IOException {
        Path zip = tempDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("nested/"));
            output.closeEntry();
            writeZipEntry(output, "nested/first.log", FIRST);
            writeZipEntry(output, "second.log", SECOND);
            writeZipEntry(output, "__MACOSX/._second.log", "metadata");
            writeZipEntry(output, ".DS_Store", "metadata");
        }

        assertEquals(List.of("nested/first.log", "second.log"), LogSourceIO.zipEntryNames(zip));
        assertEquals(2, LogSourceIO.countFiles(zip, LogSourceFormat.ZIP));
    }

    @Test
    void streamsPlainTextGZipAndFirstZipEntry() throws IOException {
        Path plainText = writePlainText("plain.log", FIRST, SECOND);
        Path gzip = writeGZip("gc.log.gz", FIRST, SECOND);
        Path zip = writeZip("gc.zip", "gc.log", FIRST, SECOND);

        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(plainText, LogSourceFormat.PLAINTEXT)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(gzip, LogSourceFormat.GZIP)));
        assertEquals(List.of(FIRST, SECOND), collect(LogSourceIO.stream(zip, LogSourceFormat.ZIP)));
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        Path zip = tempDirectory.resolve("gc.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeZipEntry(output, "first.log", FIRST);
            writeZipEntry(output, "second.log", SECOND);
        }

        assertEquals(List.of(SECOND), collect(LogSourceIO.streamZipEntry(zip, "second.log")));
    }

    @Test
    void rejectsUnreadableZipSources() throws IOException {
        Path emptyZip = tempDirectory.resolve("empty.zip");
        try (ZipOutputStream ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // Empty archive.
        }
        Path zip = writeZip("gc.zip", "gc.log", FIRST);

        assertThrows(IOException.class, () -> LogSourceIO.stream(emptyZip, LogSourceFormat.ZIP));
        assertThrows(IOException.class, () -> LogSourceIO.streamZipEntry(zip, "missing.log"));
    }

    @Test
    void readsBoundedTailAcrossLineEndings() throws IOException {
        Path lineFeed = writePlainText("lf.log", FIRST, SECOND, "third", "fourth");
        Path carriageReturnLineFeed = tempDirectory.resolve("crlf.log");
        Files.writeString(carriageReturnLineFeed, String.join("\r\n", FIRST, SECOND, "third") + "\r\n");

        assertEquals(List.of("third", "fourth"), LogSourceIO.tail(lineFeed, 2));
        assertEquals(List.of(SECOND, "third"), LogSourceIO.tail(carriageReturnLineFeed, 2));
        assertEquals(List.of(FIRST, SECOND, "third", "fourth"), LogSourceIO.tail(lineFeed, 10));
        assertEquals(List.of(), LogSourceIO.tail(lineFeed, 0));
        assertThrows(IllegalArgumentException.class, () -> LogSourceIO.tail(lineFeed, -1));
    }

    @Test
    void collectsBoundedTail() {
        List<String> tail = Stream.of(FIRST, SECOND, "third", "fourth")
                .collect(LogSourceIO.tailCollector(2));

        assertEquals(List.of("third", "fourth"), tail);
        assertEquals(List.of(), Stream.of(FIRST).collect(LogSourceIO.tailCollector(0)));
        assertThrows(IllegalArgumentException.class, () -> LogSourceIO.tailCollector(-1));
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

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

class LogSourceTest {

    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String THREE = "three";
    private static final String GC_LOG = "gc.log";
    private static final String GC_LOG_0 = "gc.log.0";
    private static final String GC_LOG_1_CURRENT = "gc.log.1.current";
    private static final String TWO_LINE_LOG = FIRST + "\n" + SECOND + "\n";
    private static final String FOUR = "four";
    private static final String UNUSED = "unused";
    private static final int LARGE_SINGLE_LINE_LENGTH = (1024 * 1024) + 1;

    @TempDir
    private Path tempDir;

    @Test
    void streamsPlainZipAndGZipSources() throws IOException {
        Path plainText = write(GC_LOG, TWO_LINE_LOG);
        Path zip = zip("gc.zip", GC_LOG, TWO_LINE_LOG);
        Path gzip = gzip("gc.log.gz", TWO_LINE_LOG);

        assertEquals(LogSourceFormat.PLAINTEXT, LogSource.detectFormat(plainText));
        assertEquals(LogSourceFormat.ZIP, LogSource.detectFormat(zip));
        assertEquals(LogSourceFormat.GZIP, LogSource.detectFormat(gzip));
        assertEquals(List.of(FIRST, SECOND), lines(LogSource.stream(plainText)));
        assertEquals(List.of(FIRST, SECOND), lines(LogSource.stream(zip)));
        assertEquals(List.of(FIRST, SECOND), lines(LogSource.stream(gzip)));
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        Path zip = zip("gc.zip", "nested/" + GC_LOG, TWO_LINE_LOG);

        assertEquals(List.of(FIRST, SECOND), lines(LogSource.streamZipEntry(zip, "nested/" + GC_LOG)));
        assertThrows(IOException.class, () -> LogSource.streamZipEntry(zip, "missing.log"));
    }

    @Test
    void normalizesLinesAndAppendsEndOfData() {
        Stream<String> lines = Stream.of(" " + FIRST + " ", "", null, SECOND);

        assertEquals(
                List.of(FIRST, SECOND, "END"),
                LogSource.withEndOfData(LogSource.nonBlankLines(lines), "END").collect(Collectors.toList()));
    }

    @Test
    void discoversDirectorySiblingAndZipSegments() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("segments"));
        Files.writeString(directory.resolve(GC_LOG_0), "0");
        Files.writeString(directory.resolve(GC_LOG_1_CURRENT), "1");
        Files.writeString(directory.resolve(GC_LOG), "2");
        Path sibling = directory.resolve(GC_LOG);
        Path zip = zip("segments.zip", GC_LOG_0, "0", GC_LOG_1_CURRENT, "1", "__MACOSX/._gc.log.0", "metadata");

        assertEquals(
                List.of(GC_LOG, GC_LOG_0, GC_LOG_1_CURRENT),
                names(LogSourceDiscovery.discoverDirectorySegments(directory)));
        assertEquals(
                List.of(GC_LOG, GC_LOG_0, GC_LOG_1_CURRENT),
                names(LogSourceDiscovery.discoverSiblingSegments(sibling, GC_LOG)));
        assertEquals(List.of(GC_LOG_0, GC_LOG_1_CURRENT), LogSourceDiscovery.discoverZipSegments(zip));
        assertEquals(GC_LOG, LogSourceDiscovery.rootPattern(GC_LOG_1_CURRENT));
        assertEquals(GC_LOG, LogSourceDiscovery.rootPattern(sibling, LogSourceFormat.DIRECTORY, names(LogSourceDiscovery.discoverDirectorySegments(directory))));
        assertEquals(GC_LOG, LogSourceDiscovery.rootPattern(sibling, LogSourceFormat.ZIP, List.of(GC_LOG_0, GC_LOG_1_CURRENT)));
        assertEquals(GC_LOG, LogSourceDiscovery.rootPattern(sibling, LogSourceFormat.PLAINTEXT, List.of()));
    }

    @Test
    void tailsFileAndStream() throws IOException {
        Path file = write("tail.log", "one\ntwo\n" + THREE + "\n" + FOUR + "\n");

        assertEquals(List.of(THREE, FOUR), LogSourceTail.readLastLines(file, 2));
        assertEquals(List.of("two", THREE, FOUR), Stream.of("one", "two", THREE, FOUR).collect(LogSourceTail.tail(3)));
        assertEquals(List.of(), LogSourceTail.readLastLines(file, 0));
    }

    @Test
    void handlesDirectoriesUnsupportedFormatsAndEmptySources() throws IOException {
        Path directory = Files.createDirectory(tempDir.resolve("logs"));
        Path emptyFile = write("empty.log", "");
        Path emptyZip = tempDir.resolve("empty.zip");
        try (var ignored = new ZipOutputStream(Files.newOutputStream(emptyZip))) {
            // The empty archive intentionally has no entries.
        }

        assertEquals(LogSourceFormat.DIRECTORY, LogSource.detectFormat(directory));
        assertThrows(IOException.class, () -> LogSource.stream(directory));
        assertThrows(IOException.class, () -> LogSource.stream(emptyZip));
        assertEquals(List.of(), LogSourceTail.readLastLines(emptyFile, 1));
    }

    @Test
    void handlesSingleLineAndCarriageReturnTails() throws IOException {
        Path singleLine = write("single.log", "one");
        Path carriageReturn = write("cr.log", "one\rtwo\r" + THREE + "\r");

        assertEquals(List.of("one"), LogSourceTail.readLastLines(singleLine, 5));
        assertEquals(List.of("two", THREE), LogSourceTail.readLastLines(carriageReturn, 2));
    }

    @Test
    void doesNotLoadLargeFileWithoutLineEndingsIntoTail() throws IOException {
        Path largeSingleLine = write("large-single-line.log", "a".repeat(LARGE_SINGLE_LINE_LENGTH));

        assertEquals(List.of(), LogSourceTail.readLastLines(largeSingleLine, 5));
    }

    private Path write(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (var outputStream = new GZIPOutputStream(Files.newOutputStream(path))) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String fileName, String entryName, String content) throws IOException {
        return zip(fileName, entryName, content, "unused.log", UNUSED, "unused-2.log", UNUSED);
    }

    private Path zip(
            String fileName,
            String firstEntry,
            String firstContent,
            String secondEntry,
            String secondContent,
            String thirdEntry,
            String thirdContent) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (var outputStream = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(outputStream, firstEntry, firstContent);
            writeZipEntry(outputStream, secondEntry, secondContent);
            writeZipEntry(outputStream, thirdEntry, thirdContent);
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream outputStream, String entryName, String content) throws IOException {
        outputStream.putNextEntry(new ZipEntry(entryName));
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        outputStream.closeEntry();
    }

    private List<String> lines(Stream<String> stream) {
        try (stream) {
            return stream.collect(Collectors.toList());
        }
    }

    private List<String> names(List<Path> paths) {
        return paths.stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toList());
    }
}

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

    private static final String CONTENT = "first line\nsecond line\nthird line\n";

    @TempDir
    Path directory;

    @Test
    void plainTextSourceIsDiscovered() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.formatOf(plainText("gc.log")));
    }

    @Test
    void gzipSourceIsDiscovered() throws IOException {
        assertEquals(LogFileFormat.GZIP, GCLogSources.formatOf(gzip("gc.log.gz")));
    }

    @Test
    void zipSourceIsDiscovered() throws IOException {
        assertEquals(LogFileFormat.ZIP, GCLogSources.formatOf(zip("gc.log.zip", "gc.log")));
    }

    @Test
    void directorySourceIsDiscovered() throws IOException {
        assertEquals(LogFileFormat.DIRECTORY, GCLogSources.formatOf(Files.createDirectory(directory.resolve("logs"))));
    }

    @Test
    void unreadableSourceIsReportedAsPlainText() {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.formatOf(directory.resolve("does-not-exist.log")));
    }

    @Test
    void shortSourceIsReportedAsPlainText() throws IOException {
        Path path = directory.resolve("tiny.log");
        Files.write(path, new byte[]{0x1F});
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.formatOf(path));
    }

    @Test
    void sizeInBytesReportsTheNumberOfBytesInTheSource() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSources.sizeInBytes(plainText("gc.log")));
    }

    @Test
    void sizeInBytesFailsForAMissingSource() {
        assertThrows(IOException.class, () -> GCLogSources.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    @Test
    void plainTextSourceIsStreamed() throws IOException {
        assertLinesMatch(List.of("first line", "second line", "third line"), collect(plainText("gc.log")));
    }

    @Test
    void gzipSourceIsStreamed() throws IOException {
        assertLinesMatch(List.of("first line", "second line", "third line"), collect(gzip("gc.log.gz")));
    }

    @Test
    void zipSourceIsStreamed() throws IOException {
        assertLinesMatch(List.of("first line", "second line", "third line"), collect(zip("gc.log.zip", "gc.log")));
    }

    @Test
    void zipSourceStreamSkipsLeadingDirectoryEntries() throws IOException {
        assertLinesMatch(List.of("first line", "second line", "third line"), collect(zip("gc.log.zip", "logs/", "logs/gc.log")));
    }

    @Test
    void directorySourceCannotBeStreamed() throws IOException {
        Path path = Files.createDirectory(directory.resolve("logs"));
        IOException failure = assertThrows(IOException.class, () -> GCLogSources.lines(path));
        assertTrue(failure.getMessage().contains(path.toString()));
    }

    @Test
    void formatMayBeSuppliedWhenStreaming() throws IOException {
        try (Stream<String> lines = GCLogSources.lines(plainText("gc.log"), LogFileFormat.PLAINTEXT)) {
            assertEquals(3, lines.count());
        }
    }

    @Test
    void entryNamesExcludeDirectories() throws IOException {
        assertLinesMatch(List.of("logs/gc.log"), GCLogSources.entryNames(zip("gc.log.zip", "logs/", "logs/gc.log")));
    }

    @Test
    void entryNamesFailForANonZipSource() throws IOException {
        Path path = plainText("gc.log");
        assertThrows(IOException.class, () -> GCLogSources.entryNames(path));
    }

    @Test
    void namedEntryIsStreamed() throws IOException {
        Path path = zip("gc.log.zip", "logs/gc.log", "logs/gc.log.1");
        try (Stream<String> lines = GCLogSources.entryLines(path, "logs/gc.log.1")) {
            assertLinesMatch(List.of("first line", "second line", "third line"), lines.collect(Collectors.toList()));
        }
    }

    @Test
    void streamingAnUnknownEntryFails() throws IOException {
        Path path = zip("gc.log.zip", "logs/gc.log");
        IOException failure = assertThrows(IOException.class, () -> GCLogSources.entryLines(path, "logs/missing.log"));
        assertTrue(failure.getMessage().contains("logs/missing.log"));
    }

    @Test
    void tailReturnsTheLastLinesOfTheSource() throws IOException {
        assertLinesMatch(List.of("second line", "third line"), GCLogSources.tail(plainText("gc.log"), 2));
    }

    @Test
    void tailReturnsEveryLineWhenTheSourceIsShorterThanRequested() throws IOException {
        assertLinesMatch(List.of("first line", "second line", "third line"), GCLogSources.tail(plainText("gc.log"), 100));
    }

    @Test
    void tailOfAnEmptySourceIsEmpty() throws IOException {
        Path path = directory.resolve("empty.log");
        Files.write(path, new byte[0]);
        assertTrue(GCLogSources.tail(path, 10).isEmpty());
    }

    @Test
    void tailReadsSourcesWithCarriageReturnLineEndings() throws IOException {
        Path path = directory.resolve("cr.log");
        Files.writeString(path, "first line\rsecond line\rthird line\r", StandardCharsets.UTF_8);
        assertLinesMatch(List.of("second line", "third line"), GCLogSources.tail(path, 2));
    }

    private List<String> collect(Path path) throws IOException {
        try (Stream<String> lines = GCLogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plainText(String name) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip(String name) throws IOException {
        Path path = directory.resolve(name);
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String name, String... entryNames) throws IOException {
        Path path = directory.resolve(name);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (String entryName : entryNames) {
                output.putNextEntry(new ZipEntry(entryName));
                if (!entryName.endsWith("/")) {
                    output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return path;
    }
}

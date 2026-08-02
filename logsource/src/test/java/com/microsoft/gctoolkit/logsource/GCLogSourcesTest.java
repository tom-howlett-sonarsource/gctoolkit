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

class GCLogSourcesTest {

    private static final String CONTENT = "first line\nsecond line\nthird line\n";

    @TempDir
    Path directory;

    @Test
    void discoversTheFormatOfEachSupportedSource() throws IOException {
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.formatOf(plainText()));
        assertEquals(LogFileFormat.GZIP, GCLogSources.formatOf(gzip()));
        assertEquals(LogFileFormat.ZIP, GCLogSources.formatOf(zip("gc.log")));
        assertEquals(LogFileFormat.DIRECTORY, GCLogSources.formatOf(directory));
        assertEquals(LogFileFormat.PLAINTEXT, GCLogSources.formatOf(empty()));
    }

    @Test
    void formatPredicatesAreMutuallyExclusive() {
        assertTrue(LogFileFormat.ZIP.isZip());
        assertTrue(LogFileFormat.GZIP.isGZip());
        assertTrue(LogFileFormat.PLAINTEXT.isPlainText());
        assertTrue(LogFileFormat.DIRECTORY.isDirectory());
        assertFalse(LogFileFormat.UNKNOWN.isPlainText());
        assertFalse(LogFileFormat.ZIP.isGZip());
    }

    @Test
    void sizesSourcesInBytes() throws IOException {
        assertEquals(CONTENT.getBytes(StandardCharsets.UTF_8).length, GCLogSources.sizeInBytes(plainText()));
        assertEquals(0L, GCLogSources.sizeInBytes(empty()));
        assertEquals(0L, GCLogSources.sizeInBytes(directory));
        assertEquals(0L, GCLogSources.sizeInBytes(directory.resolve("does-not-exist.log")));
    }

    @Test
    void readsLinesFromPlainTextZipAndGZipSources() throws IOException {
        assertEquals(List.of("first line", "second line", "third line"), collect(plainText()));
        assertEquals(List.of("first line", "second line", "third line"), collect(gzip()));
        assertEquals(List.of("first line", "second line", "third line"), collect(zip("gc.log")));
    }

    @Test
    void readsLinesFromANamedZipEntry() throws IOException {
        Path path = zip("segment.log");
        try (Stream<String> lines = GCLogSources.zipEntryLines(path, "segment.log")) {
            assertEquals(List.of("first line", "second line", "third line"), lines.collect(Collectors.toList()));
        }
        assertEquals(List.of("segment.log"), GCLogSources.zipEntryNames(path));
    }

    @Test
    void reportsSourcesThatCannotBeStreamed() throws IOException {
        assertThrows(IOException.class, () -> GCLogSources.lines(directory));
        assertThrows(IOException.class, () -> GCLogSources.lines(plainText(), LogFileFormat.UNKNOWN));
    }

    @Test
    void readsTheLastLinesOfASource() throws IOException {
        assertEquals(List.of("second line", "third line"), GCLogSources.tail(plainText(), 2));
        assertEquals(List.of(), GCLogSources.tail(empty(), 10));

        // asking for more lines than the source holds walks back to the first byte of the file,
        // which is consumed while looking for a line ending. See GCLogSources#tail.
        assertEquals(List.of("irst line", "second line", "third line"), GCLogSources.tail(plainText(), 100));
    }

    private List<String> collect(Path path) throws IOException {
        try (Stream<String> lines = GCLogSources.lines(path)) {
            return lines.collect(Collectors.toList());
        }
    }

    private Path plainText() throws IOException {
        Path path = directory.resolve("gc.log");
        Files.writeString(path, CONTENT, StandardCharsets.UTF_8);
        return path;
    }

    private Path empty() throws IOException {
        Path path = directory.resolve("empty.log");
        Files.writeString(path, "", StandardCharsets.UTF_8);
        return path;
    }

    private Path gzip() throws IOException {
        Path path = directory.resolve("gc.log.gz");
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private Path zip(String entryName) throws IOException {
        Path path = directory.resolve(entryName + ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new ZipEntry("logs/"));
            output.closeEntry();
            output.putNextEntry(new ZipEntry(entryName));
            output.write(CONTENT.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return path;
    }
}

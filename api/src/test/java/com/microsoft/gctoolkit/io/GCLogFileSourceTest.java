// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogFileSourceTest {

    private static final String END_OF_DATA = GCLogFile.END_OF_DATA_SENTINEL;
    private static final String GC_LOG = "gc.log";
    private static final String GC_ZIP = "gc.zip";

    @TempDir
    Path tempDir;

    @Test
    void streamsSinglePlainTextLogFile() throws IOException {
        Path path = writeText(GC_LOG, "  first  ", "", "second");
        SingleGCLogFile logFile = new SingleGCLogFile(path);

        assertEquals(List.of("first", "second", END_OF_DATA), lines(logFile.stream()));
        assertSame(logFile.getMetaData(), logFile.getMetaData());
        assertEquals(1, logFile.getMetaData().getNumberOfFiles());
        assertTrue(logFile.getMetaData().isPlainText());
    }

    @Test
    void streamsSingleZipAndGZipLogFiles() throws IOException {
        Path zip = writeZip(GC_ZIP, GC_LOG, "zip-line");
        Path gzip = writeGZip("gc.log.gz", "gzip-line");

        assertEquals(List.of("zip-line", END_OF_DATA), lines(new SingleGCLogFile(zip).stream()));
        assertEquals(List.of("gzip-line", END_OF_DATA), lines(new SingleGCLogFile(gzip).stream()));
        assertTrue(new SingleLogFileMetadata(zip).isZip());
        assertTrue(new SingleLogFileMetadata(gzip).isGZip());
        assertTrue(new SingleLogFileMetadata(tempDir).isDirectory());
    }

    @Test
    void readsZipSegmentMetadataAndLines() throws IOException {
        Path zip = writeZip(GC_ZIP, GC_LOG, " file created ", "1.000: first", "2.000: Saved as archived", "3.000: last");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zip, GC_LOG);

        assertEquals(zip, segment.getPath());
        assertEquals(GC_LOG, segment.getSegmentName());
        assertEquals(List.of(" file created ", "1.000: first", "2.000: Saved as archived", "3.000: last"), lines(segment.stream()));
        assertEquals(1.0d, segment.getStartTime(), 0.001d);
        assertEquals(3.0d, segment.getEndTime(), 0.001d);
        assertEquals(GC_LOG, segment.toString());
    }

    @Test
    void handlesMissingZipSegmentEntry() throws IOException {
        Path zip = writeZip(GC_ZIP, GC_LOG, "1.000: first");
        GCLogFileZipSegment segment = new GCLogFileZipSegment(zip, "missing.log");

        assertTrue(lines(segment.stream()).isEmpty());
        assertEquals(Double.MAX_VALUE, segment.getStartTime());
        assertEquals(Double.MAX_VALUE, segment.getEndTime());
    }

    private Path writeText(String fileName, String... lines) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.write(path, List.of(lines), StandardCharsets.UTF_8);
        return path;
    }

    private Path writeZip(String fileName, String entryName, String... lines) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(path))) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(String.join(System.lineSeparator(), lines).getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return path;
    }

    private Path writeGZip(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(Files.newOutputStream(path))) {
            gzipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return path;
    }

    private List<String> lines(Stream<String> stream) {
        try (stream) {
            return stream.collect(Collectors.toList());
        }
    }
}

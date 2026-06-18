// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceTest {

    private static final String CURRENT_CONTENT = "current";
    private static final String GC_LOG = "gc.log";
    private static final String GC_LOG_0 = "gc.log.0";
    private static final String GC_ZIP = "gc.zip";
    private static final String GZIP_CONTENT = "gzip-line";
    private static final String ZIP_CONTENT = "zip-line";

    @TempDir
    Path tempDir;

    @Test
    void identifiesFileFormats() throws IOException {
        Path text = writeText(GC_LOG, "first");
        Path zip = writeZip(GC_ZIP, GC_LOG, ZIP_CONTENT);
        Path gzip = writeGZip("gc.log.gz", GZIP_CONTENT);

        assertEquals(FileFormat.PLAINTEXT, GCLogSource.fileFormat(text));
        assertEquals(FileFormat.ZIP, GCLogSource.fileFormat(zip));
        assertEquals(FileFormat.GZIP, GCLogSource.fileFormat(gzip));
        assertEquals(FileFormat.DIRECTORY, GCLogSource.fileFormat(tempDir));
    }

    @Test
    void identifiesShortFilesAsPlainText() throws IOException {
        Path emptyFile = writeText("empty.log");

        assertEquals(FileFormat.PLAINTEXT, GCLogSource.fileFormat(emptyFile));
    }

    @Test
    void streamsPlainZipAndGZipSources() throws IOException {
        Path text = writeText(GC_LOG, "plain-line");
        Path zip = writeZip(GC_ZIP, GC_LOG, ZIP_CONTENT);
        Path gzip = writeGZip("gc.log.gz", GZIP_CONTENT);

        assertEquals(List.of("plain-line"), lines(GCLogSource.stream(text, FileFormat.PLAINTEXT)));
        assertEquals(List.of(ZIP_CONTENT), lines(GCLogSource.stream(zip, FileFormat.ZIP)));
        assertEquals(List.of(GZIP_CONTENT), lines(GCLogSource.stream(gzip, FileFormat.GZIP)));
    }

    @Test
    void rejectsUnsupportedStreamFormats() {
        IOException exception = assertThrows(IOException.class, () -> GCLogSource.stream(tempDir, FileFormat.DIRECTORY));

        assertTrue(exception.getMessage().contains("Unable to read"));
    }

    @Test
    void streamsNamedZipEntry() throws IOException {
        Path zip = writeZip(GC_ZIP, "gc.log.1", "first", GC_LOG, CURRENT_CONTENT);

        assertEquals(List.of(CURRENT_CONTENT), lines(GCLogSource.streamZipEntry(zip, GC_LOG)));
        assertEquals(List.of("gc.log.1", GC_LOG), GCLogSource.zipEntryNames(zip));
    }

    @Test
    void returnsEmptyStreamForMissingZipEntry() throws IOException {
        Path zip = writeZip(GC_ZIP, GC_LOG, CURRENT_CONTENT);

        assertTrue(lines(GCLogSource.streamZipEntry(zip, "missing.log")).isEmpty());
    }

    @Test
    void streamsFirstNonDirectoryZipEntry() throws IOException {
        Path zip = tempDir.resolve(GC_ZIP);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOutputStream.putNextEntry(new ZipEntry("logs/"));
            zipOutputStream.closeEntry();
            writeZipEntry(zipOutputStream, GC_LOG, CURRENT_CONTENT);
        }

        assertEquals(List.of(CURRENT_CONTENT), lines(GCLogSource.stream(zip, FileFormat.ZIP)));
    }

    @Test
    void returnsEmptyStreamForZipWithoutFileEntries() throws IOException {
        Path zip = tempDir.resolve(GC_ZIP);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zip))) {
            zipOutputStream.putNextEntry(new ZipEntry("logs/"));
            zipOutputStream.closeEntry();
        }

        assertTrue(lines(GCLogSource.stream(zip, FileFormat.ZIP)).isEmpty());
    }

    @Test
    void discoversRotatingLogSegments() throws IOException {
        writeText(GC_LOG, CURRENT_CONTENT);
        writeText(GC_LOG_0, "previous");
        writeText("other.log", "ignored");

        List<String> names = GCLogSource.discoverSegments(tempDir.resolve(GC_LOG), FileFormat.PLAINTEXT, GC_LOG).stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(GC_LOG, GC_LOG_0), names);
        assertEquals(GC_LOG, GCLogSource.rootPattern(names, tempDir.resolve(GC_LOG), FileFormat.PLAINTEXT));
    }

    @Test
    void discoversDirectorySegments() throws IOException {
        writeText(GC_LOG, CURRENT_CONTENT);
        writeText(GC_LOG_0, "previous");

        List<String> names = GCLogSource.discoverSegments(tempDir, FileFormat.DIRECTORY, "ignored").stream()
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());

        assertEquals(List.of(GC_LOG, GC_LOG_0), names);
    }

    @Test
    void derivesRootPatternsForDirectoryZipAndEmptyInputs() {
        assertEquals(GC_LOG, GCLogSource.rootPattern(List.of(GC_LOG_0), tempDir, FileFormat.DIRECTORY));
        assertEquals(GC_LOG, GCLogSource.rootPattern(List.of("gc.log.1.current"), tempDir.resolve(GC_ZIP), FileFormat.ZIP));
        assertEquals("", GCLogSource.rootPattern(List.of(), tempDir, FileFormat.DIRECTORY));
    }

    @Test
    void providesSegmentMetadataHelpers() {
        Path current = tempDir.resolve(GC_LOG);
        Path indexed = tempDir.resolve("gc.log.7.current");
        Path previous = tempDir.resolve("gc.log.7");

        assertTrue(GCLogSource.isCurrentSegment(current));
        assertTrue(GCLogSource.isCurrentSegment(indexed));
        assertFalse(GCLogSource.isCurrentSegment(previous));
        assertEquals(Integer.MAX_VALUE, GCLogSource.segmentIndex(current));
        assertEquals(7, GCLogSource.segmentIndex(indexed));
    }

    @Test
    void keepsOnlyRequestedTailLines() throws IOException {
        Path text = writeText(GC_LOG, "1", "2", "3", "4");

        assertEquals(List.of("3", "4"), GCLogSource.tail(text, 2));
        assertTrue(Stream.of("1", "2").collect(GCLogSource.tail(0)).isEmpty());
        assertEquals(3, IntStream.rangeClosed(1, 10).parallel().boxed().collect(GCLogSource.tail(3)).size());
    }

    private Path writeText(String fileName, String... lines) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.write(path, List.of(lines), StandardCharsets.UTF_8);
        return path;
    }

    private Path writeZip(String fileName, String entryName, String content) throws IOException {
        return writeZip(fileName, entryName, content, null, null);
    }

    private Path writeZip(String fileName, String firstEntryName, String firstContent, String secondEntryName, String secondContent) throws IOException {
        Path path = tempDir.resolve(fileName);
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(path))) {
            writeZipEntry(zipOutputStream, firstEntryName, firstContent);
            if (secondEntryName != null) {
                writeZipEntry(zipOutputStream, secondEntryName, secondContent);
            }
        }
        return path;
    }

    private void writeZipEntry(ZipOutputStream zipOutputStream, String entryName, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
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

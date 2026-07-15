// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.microsoft.gctoolkit.time.DateTimeStamp;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GCLogSourceIntegrationTest {

    private static final String FIRST = "[1.000s][info][gc] first";
    private static final String SECOND = "[2.000s][info][gc] second";
    private static final String THIRD = "[3.000s][info][gc] third";
    private static final String FOURTH = "[4.000s][info][gc] fourth";

    @TempDir
    private Path tempDirectory;

    @Test
    void streamsEverySingleSourceFormat() throws IOException {
        Path plainText = Files.write(tempDirectory.resolve("gc.log"), List.of(FIRST, SECOND));
        Path gzip = writeGZip("gc.log.gz", FIRST, SECOND);
        Path zip = writeZip("gc.zip", List.of(new LogEntry("gc.log", FIRST, SECOND)));

        assertSingleSource(plainText, true, false, false);
        assertSingleSource(gzip, false, true, false);
        assertSingleSource(zip, false, false, true);
    }

    @Test
    void discoversOrdersAndStreamsRotatingDirectory() throws IOException {
        Path directory = Files.createDirectory(tempDirectory.resolve("rotating"));
        Path previous = Files.write(directory.resolve("gc.log.0"), List.of(FIRST, SECOND));
        Path current = Files.write(directory.resolve("gc.log"), List.of(THIRD, FOURTH));
        RotatingGCLogFile source = new RotatingGCLogFile(directory);

        LogFileMetadata metadata = source.getMetaData();
        assertSame(metadata, source.getMetaData());
        assertTrue(metadata.isDirectory());
        assertFalse(metadata.isPlainText());
        assertEquals(2, metadata.getNumberOfFiles());
        assertEquals(List.of(previous, current), source.getOrderedGarbageCollectionLogFiles().stream()
                .map(LogFileSegment::getPath)
                .collect(Collectors.toList()));
        assertEquals(List.of(FIRST, SECOND, THIRD, FOURTH, GCLogFile.END_OF_DATA_SENTINEL), collect(source.stream()));

        GCLogFileSegment previousSegment = new GCLogFileSegment(previous);
        assertEquals(0, previousSegment.getSegmentIndex());
        assertFalse(previousSegment.isCurrent());
        assertEquals("gc.log.0", previousSegment.getSegmentName());
        assertEquals("gc.log.0", previousSegment.toString());
        assertEquals(1.0d, previousSegment.getStartTime(), 0.0001d);
        assertEquals(2.0d, previousSegment.getEndTime(), 0.0001d);
        assertEquals(List.of(FIRST, SECOND), collect(previousSegment.stream()));

        GCLogFileSegment currentSegment = new GCLogFileSegment(current);
        assertEquals(Integer.MAX_VALUE, currentSegment.getSegmentIndex());
        assertTrue(currentSegment.isCurrent());
    }

    @Test
    void discoversOrdersAndStreamsRotatingZip() throws IOException {
        Path zip = writeZip("rotating.zip", List.of(
                new LogEntry("gc.log.0", FIRST, SECOND),
                new LogEntry("gc.log.1.current", THIRD, FOURTH)));
        RotatingGCLogFile source = new RotatingGCLogFile(zip);

        LogFileMetadata metadata = source.getMetaData();
        assertTrue(metadata.isZip());
        assertEquals(2, metadata.getNumberOfFiles());
        assertEquals(List.of("gc.log.0", "gc.log.1.current"), source.getOrderedGarbageCollectionLogFiles().stream()
                .map(LogFileSegment::getSegmentName)
                .collect(Collectors.toList()));
        assertEquals(List.of(FIRST, SECOND, THIRD, FOURTH, GCLogFile.END_OF_DATA_SENTINEL), collect(source.stream()));

        GCLogFileZipSegment segment = new GCLogFileZipSegment(zip, "gc.log.0");
        assertEquals(zip, segment.getPath());
        assertEquals("gc.log.0", segment.getSegmentName());
        assertEquals("gc.log.0", segment.toString());
        assertEquals(1.0d, segment.getStartTime(), 0.0001d);
        assertEquals(2.0d, segment.getEndTime(), 0.0001d);
        assertEquals(List.of(FIRST, SECOND), collect(segment.stream()));
        assertEquals(List.of(SECOND), Stream.of(FIRST, SECOND).collect(segment.tail(1)));
    }

    @Test
    void returnsEmptySegmentStreamsForUnreadableSources() throws IOException {
        GCLogFileSegment missingFile = new GCLogFileSegment(tempDirectory.resolve("missing.log"));
        Path zip = writeZip("gc.zip", List.of(new LogEntry("gc.log", FIRST)));
        GCLogFileZipSegment missingEntry = new GCLogFileZipSegment(zip, "missing.log");

        assertEquals(List.of(), collect(missingFile.stream()));
        assertEquals(List.of(), collect(missingEntry.stream()));
    }

    @Test
    void derivesZipSegmentBoundsFromDatesOrUsesTheUnknownTimeFallback() throws IOException {
        String date = "2018-04-04T09:10:00.586+0000";
        Path zip = writeZip("timestamps.zip", List.of(
                new LogEntry("dated.log", "[" + date + "][info][gc] event"),
                new LogEntry("undated.log", "[info][gc] event")));

        GCLogFileZipSegment dated = new GCLogFileZipSegment(zip, "dated.log");
        double expectedEpoch = new DateTimeStamp(date).toEpochInMillis();
        assertEquals(expectedEpoch, dated.getStartTime(), 0.0001d);
        assertEquals(expectedEpoch, dated.getEndTime(), 0.0001d);

        GCLogFileZipSegment undated = new GCLogFileZipSegment(zip, "undated.log");
        assertEquals(Double.MAX_VALUE, undated.getStartTime());
        assertEquals(Double.MAX_VALUE, undated.getEndTime());
    }

    private void assertSingleSource(Path path, boolean plainText, boolean gzip, boolean zip) throws IOException {
        SingleGCLogFile source = new SingleGCLogFile(path);
        LogFileMetadata metadata = source.getMetaData();

        assertSame(metadata, source.getMetaData());
        assertEquals(path, metadata.getPath());
        assertEquals(1, metadata.getNumberOfFiles());
        assertEquals(1, metadata.logFiles().count());
        assertEquals(plainText, metadata.isPlainText());
        assertEquals(gzip, metadata.isGZip());
        assertEquals(zip, metadata.isZip());
        assertFalse(metadata.isDirectory());
        assertEquals(List.of(FIRST, SECOND, GCLogFile.END_OF_DATA_SENTINEL), collect(source.stream()));
    }

    private Path writeGZip(String fileName, String... lines) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(path))) {
            output.write(content(lines));
        }
        return path;
    }

    private Path writeZip(String fileName, List<LogEntry> entries) throws IOException {
        Path path = tempDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (LogEntry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name));
                output.write(content(entry.lines));
                output.closeEntry();
            }
        }
        return path;
    }

    private byte[] content(String... lines) {
        return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private List<String> collect(Stream<String> stream) {
        try (Stream<String> lines = stream) {
            return lines.collect(Collectors.toList());
        }
    }

    private static final class LogEntry {
        private final String name;
        private final String[] lines;

        private LogEntry(String name, String... lines) {
            this.name = name;
            this.lines = lines;
        }
    }
}

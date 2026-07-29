// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stream {@link RotatingGCLogFile} composes out of its segments owns the segment streams:
 * closing it has to close every segment stream it opened, in every consumption pattern, while
 * still handing the caller the segment contents in rotating order followed by the sentinel.
 */
class RotatingGCLogFileStreamTest {

    @TempDir
    Path directory;

    @Test
    void closingTheDrainedStreamClosesEverySegmentStream() throws IOException {
        List<RecordingSegment> segments = segments();
        RotatingGCLogFile logFile = rotatingLogFile(segments);

        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = logFile.stream()) {
            stream.forEach(lines::add);
        }

        assertEquals(Arrays.asList("oldest", "middle", "current", GCLogFile.END_OF_DATA_SENTINEL), lines);
        segments.forEach(segment -> assertEquals(1, segment.closes(), segment + " was not closed exactly once"));
    }

    @Test
    void closingAPartiallyConsumedStreamClosesEverySegmentStreamThatWasOpened() throws IOException {
        List<RecordingSegment> segments = segments();
        RotatingGCLogFile logFile = rotatingLogFile(segments);

        try (Stream<String> stream = logFile.stream()) {
            Iterator<String> lines = stream.iterator();
            assertEquals("oldest", lines.next());
        }

        segments.stream()
                .filter(segment -> segment.opens() > 0)
                .forEach(segment -> assertEquals(1, segment.closes(), segment + " was left open"));
        assertTrue(segments.get(0).opens() > 0, "the first segment must have been read");
    }

    @Test
    void closingAnUnusedStreamClosesNothing() throws IOException {
        List<RecordingSegment> segments = segments();
        RotatingGCLogFile logFile = rotatingLogFile(segments);

        logFile.stream().close();

        segments.forEach(segment -> assertEquals(0, segment.opens(), segment + " should not have been read"));
    }

    @Test
    void aShortCircuitedStreamClosesTheSegmentItStoppedIn() throws IOException {
        List<RecordingSegment> segments = segments();
        RotatingGCLogFile logFile = rotatingLogFile(segments);

        try (Stream<String> stream = logFile.stream()) {
            assertEquals("oldest", stream.findFirst().orElseThrow(AssertionError::new));
        }

        assertEquals(1, segments.get(0).closes());
    }

    @Test
    void blankAndPaddedSegmentLinesAreTrimmedAndDroppedAsBefore() throws IOException {
        List<RecordingSegment> segments = Arrays.asList(
                new RecordingSegment("one", Arrays.asList("  padded  ", "   ", "")),
                new RecordingSegment("two", Arrays.asList("", "kept")));
        RotatingGCLogFile logFile = rotatingLogFile(segments);

        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = logFile.stream()) {
            stream.forEach(lines::add);
        }

        assertEquals(Arrays.asList("padded", "kept", GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    @Test
    void anUnreadableLogFileStreamsNothingButTheSentinel() throws IOException {
        Path archive = directory.resolve("gc.log.gz");
        Files.write(archive, new byte[]{0x1f, (byte) 0x8b, 0x08, 0x00});

        List<String> lines = new ArrayList<>();
        try (Stream<String> stream = new RotatingGCLogFile(archive).stream()) {
            stream.forEach(lines::add);
        }

        assertEquals(Arrays.asList(GCLogFile.END_OF_DATA_SENTINEL), lines);
    }

    private List<RecordingSegment> segments() {
        return Arrays.asList(
                new RecordingSegment("gc.log.0", Arrays.asList("oldest")),
                new RecordingSegment("gc.log.1", Arrays.asList("middle")),
                new RecordingSegment("gc.log", Arrays.asList("current")));
    }

    private RotatingGCLogFile rotatingLogFile(List<? extends LogFileSegment> segments) throws IOException {
        Path path = directory.resolve("gc.log");
        Files.write(path, "[0.100s][info][gc] plain text\n".getBytes(StandardCharsets.UTF_8));
        return new StubRotatingGCLogFile(path, new StubMetadata(path, segments));
    }

    /**
     * A {@link RotatingGCLogFile} whose segments are supplied by the test rather than discovered
     * on disk, so that the lifecycle of each segment stream can be observed directly.
     */
    private static class StubRotatingGCLogFile extends RotatingGCLogFile {

        private final LogFileMetadata metadata;

        StubRotatingGCLogFile(Path path, LogFileMetadata metadata) {
            super(path);
            this.metadata = metadata;
        }

        @Override
        public LogFileMetadata getMetaData() {
            return metadata;
        }
    }

    private static class StubMetadata extends LogFileMetadata {

        private final List<? extends LogFileSegment> segments;

        StubMetadata(Path path, List<? extends LogFileSegment> segments) throws IOException {
            super(path);
            this.segments = segments;
        }

        @Override
        public Stream<LogFileSegment> logFiles() {
            return segments.stream().map(LogFileSegment.class::cast);
        }

        @Override
        public int getNumberOfFiles() {
            return segments.size();
        }
    }

    private static class RecordingSegment implements LogFileSegment {

        private final String name;
        private final List<String> lines;
        private int opens;
        private int closes;

        RecordingSegment(String name, List<String> lines) {
            this.name = name;
            this.lines = lines;
        }

        @Override
        public Path getPath() {
            return Path.of(name);
        }

        @Override
        public String getSegmentName() {
            return name;
        }

        @Override
        public double getStartTime() {
            return 0.0d;
        }

        @Override
        public double getEndTime() {
            return 0.0d;
        }

        @Override
        public Stream<String> stream() {
            opens++;
            return lines.stream().onClose(() -> closes++);
        }

        int opens() {
            return opens;
        }

        int closes() {
            return closes;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}

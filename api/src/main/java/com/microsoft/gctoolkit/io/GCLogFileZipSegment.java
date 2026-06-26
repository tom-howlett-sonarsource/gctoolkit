// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.source.GCLogFileLineSource;
import com.microsoft.gctoolkit.io.source.GCLogFileTail;
import com.microsoft.gctoolkit.time.DateTimeStamp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * A {@link RotatingGCLogFile} is made up of {@code GarbageCollectionLogFileSegment}s. Creating
 * a {@code GarbageCollectionLogFileSegment} is not necessary when the
 * {@link RotatingGCLogFile#RotatingGCLogFile(Path)} constructor is used.
 * The {@link RotatingGCLogFile#RotatingGCLogFile(Path)} constructor allows the user to
 * provide a list of discrete {@code GarbageCollectionLogFileSegement}s for a {@code RotatingGCLogFile}.
 */
public class GCLogFileZipSegment implements LogFileSegment {

    private static final Logger LOGGER = Logger.getLogger(GCLogFileZipSegment.class.getName());

    private final Path path;
    private final String segmentName;
    private DateTimeStamp endTime = null;
    private DateTimeStamp startTime = null;

    /**
     * The constructor attempts to extract the segment index from the file name.
     * @param path The path to the file.
     * @param segmentName name of first segment in zip file
     */
    public GCLogFileZipSegment(Path path, String segmentName) {
        this.path = path;
        this.segmentName = segmentName;
    }

    /**
     * Return the path to the file.
     * @return The path to the file.
     */
    public Path getPath() {
        return path;
    }

    public String getSegmentName() {
        return this.segmentName;
    }

    private void ageOfJVMAtLogStart() {
        if (startTime == null) {
            try (Stream<String> lines = stream()) {
                startTime = lines
                        .filter(s -> ! s.contains(" file created "))
                        .map(DateTimeStamp::fromGCLogLine)
                        .filter(dateTimeStamp -> dateTimeStamp.hasTimeStamp() || dateTimeStamp.hasDateStamp())
                        .findFirst()
                        .orElse(new DateTimeStamp(-1.0d));
            }
        }
    }

    private DateTimeStamp ageOfJVMAtLogEnd()  {
        if (endTime == null) {
            List<String> tailLines;
            try (Stream<String> lines = stream()) {
                tailLines = lines.collect(tail(100));
            }
            endTime = tailLines.stream()
                    .filter(line -> ! line.contains("Saved as"))
                    .map(DateTimeStamp::fromGCLogLine)
                    .filter(dateTimeStamp -> dateTimeStamp.hasTimeStamp() || dateTimeStamp.hasDateStamp())
                    .max(Comparator.comparing(dateTimeStamp -> dateTimeStamp != null ? dateTimeStamp.getTimeStamp() : 0))
                    .orElse(DateTimeStamp.EMPTY_DATE);
        }
        return endTime;
    }

    /**
     * A {@link Collector} that retains only the last {@code n} lines of a stream.
     * @param n the number of trailing lines to keep.
     * @param <T> the element type.
     * @return a collector yielding the last {@code n} lines.
     */
    public <T> Collector<T, ?, List<T>> tail(int n) {
        return GCLogFileTail.collector(n);
    }

    @Override
    public double getStartTime() {
        try {
            ageOfJVMAtLogStart();
            if ( startTime.hasTimeStamp())
                return startTime.getTimeStamp();
            else if ( startTime.hasDateStamp())
                return startTime.toEpochInMillis();
            else
                return Double.MAX_VALUE;
        } catch (NullPointerException ex) {
            return Double.MIN_VALUE;
        }
    }

    @Override
    public double getEndTime() {
        try {
            ageOfJVMAtLogEnd();
            if ( endTime.hasTimeStamp())
                return endTime.getTimeStamp();
            else if ( endTime.hasDateStamp())
                return endTime.toEpochInMillis();
            else
                return Double.MAX_VALUE;
        } catch (NullPointerException ex) {
            return Double.MIN_VALUE;
        }
    }

    /**
     * Stream the file, one line at a time.
     * @return A stream of lines from the file.
     */
    public Stream<String> stream() {
        try {
            return GCLogFileLineSource.zipEntry(path, segmentName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Unable to stream " + segmentName + " from " + path);
        }
        return Stream.empty();
    }

    /**
     * {@inheritDoc}
     * @return Returns {@code this.getPath().toString(); }
     */
    @Override
    public String toString() {
        return getSegmentName();
    }
}

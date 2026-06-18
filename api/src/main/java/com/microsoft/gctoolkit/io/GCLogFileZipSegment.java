// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gcsource.GCLogSources;
import com.microsoft.gctoolkit.time.DateTimeStamp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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
            startTime = stream()
                    .filter(s -> ! s.contains(" file created "))
                    .map(DateTimeStamp::fromGCLogLine)
                    .filter(dateTimeStamp -> dateTimeStamp.hasTimeStamp() || dateTimeStamp.hasDateStamp())
                    .findFirst()
                    .orElse(new DateTimeStamp(-1.0d));
        }
    }

    private DateTimeStamp ageOfJVMAtLogEnd()  {
        if (endTime == null) {
            List<String> tail = stream().
                    collect(GCLogSources.tailCollector(100));
            endTime = tail.stream()
                    .filter(line -> ! line.contains("Saved as"))
                    .map(DateTimeStamp::fromGCLogLine)
                    .filter(dateTimeStamp -> dateTimeStamp.hasTimeStamp() || dateTimeStamp.hasDateStamp())
                    .max(Comparator.comparing(dateTimeStamp -> dateTimeStamp != null ? sortableTime(dateTimeStamp) : 0))
                    .orElse(DateTimeStamp.EMPTY_DATE);
        }
        return endTime;
    }

    @Override
    public double getStartTime() {
        try {
            ageOfJVMAtLogStart();
            return sortableTime(startTime);
        } catch (NullPointerException ex) {
            return Double.MIN_VALUE;
        }
    }

    @Override
    public double getEndTime() {
        try {
            ageOfJVMAtLogEnd();
            return sortableTime(endTime);
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
            return GCLogSources.streamZipEntry(path, segmentName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "Unable to stream log segment " + segmentName + " from " + path);
        }
        return new ArrayList<String>().stream();
    }

    private static double sortableTime(DateTimeStamp dateTimeStamp) {
        if (dateTimeStamp.hasTimeStamp()) {
            return dateTimeStamp.toSeconds();
        }
        if (dateTimeStamp.hasDateStamp()) {
            return dateTimeStamp.toEpochInMillis();
        }
        return Double.MAX_VALUE;
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

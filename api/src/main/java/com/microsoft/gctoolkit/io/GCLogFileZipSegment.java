// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.time.DateTimeStamp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
                        .filter(s -> !s.contains(" file created "))
                        .map(DateTimeStamp::fromGCLogLine)
                        .filter(dateTimeStamp -> dateTimeStamp.hasTimeStamp() || dateTimeStamp.hasDateStamp())
                        .findFirst()
                        .orElse(new DateTimeStamp(-1.0d));
            }
        }
    }

    private DateTimeStamp ageOfJVMAtLogEnd()  {
        if (endTime == null) {
            List<String> tail;
            try (Stream<String> lines = stream()) {
                tail = lines.collect(tail(100));
            }
            endTime = tail.stream()
                    .filter(line -> ! line.contains("Saved as"))
                    .map(DateTimeStamp::fromGCLogLine)
                    .filter(dateTimeStamp -> dateTimeStamp.hasTimeStamp() || dateTimeStamp.hasDateStamp())
                    .max(Comparator.comparing(dateTimeStamp -> dateTimeStamp != null ? dateTimeStamp.getTimeStamp() : 0))
                    .orElse(DateTimeStamp.EMPTY_DATE);
        }
        return endTime;
    }

    public <T> Collector<T, ?, List<T>> tail(int n) {
        return Collector.<T, Deque<T>, List<T>>of(ArrayDeque::new, (buffer, line) -> {
            if(buffer.size() == n)
                buffer.pollFirst();
            buffer.add(line);
        }, (buffer, list) -> {
            while(list.size() < n && !buffer.isEmpty()) {
                list.addFirst(buffer.pollLast());
            }
            return list;
        }, ArrayList::new);
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
    @SuppressWarnings("java:S2095") // Ownership of the archive is transferred to the returned stream.
    public Stream<String> stream() {
        ZipFile file = null;
        try {
            file = new ZipFile(path.toFile());
            ZipEntry entry = file.getEntry(this.segmentName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(entry)));
            ZipFile archive = file;
            return reader.lines().onClose(() -> close(reader, archive));
        } catch (IOException e) {
            closeAfterFailure(file, e);
            LOGGER.log(Level.WARNING, e, () -> "Unable to stream ZIP segment " + segmentName);
        } catch (RuntimeException failure) {
            closeAfterFailure(file, failure);
            throw failure;
        }
        return Stream.empty();
    }

    private static void close(BufferedReader reader, ZipFile file) {
        try (ZipFile archive = file; BufferedReader input = reader) {
            // Closing the resources is the only work required here.
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static void closeAfterFailure(ZipFile file, Throwable failure) {
        if (file == null) {
            return;
        }
        try {
            file.close();
        } catch (IOException ioe) {
            failure.addSuppressed(ioe);
        }
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

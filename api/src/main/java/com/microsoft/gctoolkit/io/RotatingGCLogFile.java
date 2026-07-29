// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A collection of rotating GC log files. The collection will contain only those files that can be
 * considered contiguous. The log file segments are ordered, with the current or newest file first.
 */
public class RotatingGCLogFile extends GCLogFile {

    /**
     * Use the given path to find rotating log files. If the path is a file, the file name is used to match
     * other files in the directory. If the path is a directory, all files in the directory are considered.
     * @param path the path to a rotating log file, or to a directory containing rotating log files.
     */
    public RotatingGCLogFile(Path path) {
        super(path);
    }

    private RotatingLogFileMetadata metaData;

    public LogFileMetadata getMetaData() throws IOException {
        if ( metaData == null)
            metaData =  new RotatingLogFileMetadata(getPath());
        return metaData;
    }

    @Override
    public Stream<String> stream() throws IOException {
        if ( getMetaData().isDirectory() || getMetaData().isPlainText() || getMetaData().isZip()) {
            // A segment stream is opened only when the composed stream reaches that segment. Each one
            // is recorded so that closing the composed stream releases the segments opened so far, even
            // when the caller closes it before all of the segments have been consumed.
            List<Stream<String>> openedSegments = new CopyOnWriteArrayList<>();
            return Stream.concat(
                    getMetaData().logFiles()
                    .flatMap(segment -> open(segment, openedSegments))
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> s.length() > 0),
                    Stream.of(endOfData()))
                    .onClose(() -> closeAll(openedSegments));
        }
        else // yes, this is returning an empty stream.
            return Stream.of(endOfData());
    }

    private static Stream<String> open(LogFileSegment segment, List<Stream<String>> openedSegments) {
        Stream<String> lines = segment.stream();
        if (lines != null)
            openedSegments.add(lines);
        return lines;
    }

    /**
     * Close every segment stream, even if one of them fails to close. The first failure is
     * reported, the rest are suppressed.
     */
    private static void closeAll(List<Stream<String>> openedSegments) {
        RuntimeException failure = null;
        for (Stream<String> segment : openedSegments) {
            try {
                segment.close();
            } catch (RuntimeException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
        }
        if (failure != null)
            throw failure;
    }

    /**
     * The {@link GCLogFileSegment}s in rotating order. Note that only the contiguous
     * log file segments are included. Therefore, the number of log file segments may be less than
     * the files that match the rotating pattern.
     * @return The log file segments in rotating order.
     * @throws IOException when there is an IO exception
     */
    public List<LogFileSegment> getOrderedGarbageCollectionLogFiles() throws IOException {
        return getMetaData().logFiles().collect(Collectors.toList());
    }
}

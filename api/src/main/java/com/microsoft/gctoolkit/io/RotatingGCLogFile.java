// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
        LogFileMetadata metadata = getMetaData();
        if ( metadata.isDirectory() || metadata.isPlainText() || metadata.isZip()) {
            Queue<Stream<String>> openSegmentStreams = new ConcurrentLinkedQueue<>();
            Stream<String> lines = metadata.logFiles()
                    .flatMap(segment -> openSegment(segment, openSegmentStreams))
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty());
            return Stream.concat(lines, Stream.of(endOfData()))
                    .onClose(() -> close(openSegmentStreams));
        } else { // yes, this is returning an empty stream.
            return Stream.of(endOfData());
        }
    }

    private static Stream<String> openSegment(LogFileSegment segment, Queue<Stream<String>> openSegmentStreams) {
        Stream<String> stream = segment.stream();
        if (stream == null) {
            return null;
        }
        openSegmentStreams.add(stream);
        return stream.onClose(() -> openSegmentStreams.remove(stream));
    }

    private static void close(Queue<Stream<String>> streams) {
        RuntimeException failure = null;
        Stream<String> stream;
        while ((stream = streams.poll()) != null) {
            try {
                stream.close();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
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

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
        LogFileMetadata metadata = getMetaData();
        if ( ! (metadata.isDirectory() || metadata.isPlainText() || metadata.isZip()))
            // yes, this is returning an empty stream.
            return Stream.of(endOfData());

        // The segments are opened one at a time, as the composed stream reaches them. Closing the
        // composed stream, whether it has been fully consumed or abandoned part way through, has to
        // release the segments opened so far.
        List<Stream<String>> openedSegments = new CopyOnWriteArrayList<>();
        Stream<String> lines = metadata.logFiles()
                .flatMap(segment -> {
                    Stream<String> segmentLines = segment.stream();
                    if (segmentLines != null)
                        openedSegments.add(segmentLines);
                    return segmentLines;
                })
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> s.length() > 0);
        return Stream.concat(lines, Stream.of(endOfData()))
                .onClose(() -> LineStreams.closeAll(openedSegments));
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

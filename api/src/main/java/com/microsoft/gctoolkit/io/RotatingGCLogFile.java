// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A collection of rotating GC log files. The collection will contain only those files that can be
 * considered contiguous. The log file segments are ordered, with the current or newest file first.
 */
public class RotatingGCLogFile extends GCLogFile {

    private static final Logger LOGGER = Logger.getLogger(RotatingGCLogFile.class.getName());

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

        // Each segment stream holds an archive, or a file, open. flatMap undertakes to close a
        // mapped stream once its contents have been placed into this stream, which says nothing
        // about a segment abandoned by a short-circuited or partially consumed stream. So every
        // segment stream handed out is tracked and released when this stream is closed; closing a
        // stream twice is a no-op, so the segments flatMap has already drained are unaffected.
        List<Stream<String>> segmentStreams = Collections.synchronizedList(new ArrayList<>());
        Stream<String> lines = metadata.logFiles()
                .flatMap(segment -> track(segment.stream(), segmentStreams))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> ! s.isEmpty());
        return Stream.concat(lines, Stream.of(endOfData()))
                .onClose(() -> LogFileStreams.closeAll(segmentStreams));
    }

    private static Stream<String> track(Stream<String> segmentStream, List<Stream<String>> openStreams) {
        if (segmentStream != null)
            openStreams.add(segmentStream);
        return segmentStream;
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

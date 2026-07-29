// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
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

    /**
     * Stream the segments, in rotating order, followed by the end of data sentinel.
     * The segments are opened one at a time and each is released as the stream advances
     * beyond it, so a caller that closes a partially consumed stream leaves no segment
     * open. This relies on each segment releasing its own resources when its stream is
     * closed; see {@link GCLogFileZipSegment#stream()}.
     * @return A stream of the lines in all of the log file segments.
     * @throws IOException when there is an IO exception
     */
    @Override
    public Stream<String> stream() throws IOException {
        LogFileMetadata metadata = getMetaData();
        if ( ! (metadata.isDirectory() || metadata.isPlainText() || metadata.isZip()))
            // yes, this is returning an empty stream.
            return Stream.of(endOfData());

        return Stream.concat(
                metadata.logFiles()
                        .flatMap(LogFileSegment::stream)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> ! s.isEmpty()),
                Stream.of(endOfData()));
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

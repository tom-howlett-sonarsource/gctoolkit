// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
        if ( getMetaData().isDirectory() || getMetaData().isPlainText() || getMetaData().isZip()) {
            SegmentLineSpliterator lines = new SegmentLineSpliterator(getMetaData().logFiles());
            Stream<String> segmentLines = StreamSupport.stream(lines, false).onClose(lines::close);
            return Stream.concat(segmentLines
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> s.length() > 0),
                    Stream.of(endOfData()));
        } else { // yes, this is returning an empty stream.
            return Stream.of(endOfData());
        }
    }

    private static final class SegmentLineSpliterator extends Spliterators.AbstractSpliterator<String>
            implements AutoCloseable {

        private final Stream<LogFileSegment> segments;
        private final Iterator<LogFileSegment> segmentIterator;
        private Stream<String> currentStream;
        private Iterator<String> currentIterator;
        private boolean closed;

        private SegmentLineSpliterator(Stream<LogFileSegment> segments) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.segments = segments;
            this.segmentIterator = segments.iterator();
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            Objects.requireNonNull(action);
            if (closed) {
                return false;
            }

            while (true) {
                if (currentIterator != null && currentIterator.hasNext()) {
                    action.accept(currentIterator.next());
                    return true;
                }

                closeCurrentStream();
                if (!segmentIterator.hasNext()) {
                    close();
                    return false;
                }

                currentStream = segmentIterator.next().stream();
                currentIterator = currentStream == null ? null : currentStream.iterator();
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            RuntimeException failure = null;
            try {
                closeCurrentStream();
            } catch (RuntimeException rte) {
                failure = rte;
            }
            try {
                segments.close();
            } catch (RuntimeException rte) {
                if (failure == null) {
                    failure = rte;
                } else {
                    failure.addSuppressed(rte);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void closeCurrentStream() {
            if (currentStream != null) {
                try {
                    currentStream.close();
                } finally {
                    currentStream = null;
                    currentIterator = null;
                }
            }
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

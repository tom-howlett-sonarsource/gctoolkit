// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
        if ( getMetaData().isDirectory() || getMetaData().isPlainText() || getMetaData().isZip())
            return Stream.concat(
                    streamSegments(getMetaData().logFiles())
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> s.length() > 0),
                    Stream.of(endOfData()));
        else // yes, this is returning an empty stream.
            return Stream.of(endOfData());
    }

    private Stream<String> streamSegments(Stream<LogFileSegment> segments) {
        ClosingSegmentSpliterator spliterator = new ClosingSegmentSpliterator(segments);
        return StreamSupport.stream(spliterator, false).onClose(spliterator::close);
    }

    private static final class ClosingSegmentSpliterator extends Spliterators.AbstractSpliterator<String> {
        private final Stream<LogFileSegment> segments;
        private final Iterator<LogFileSegment> segmentIterator;
        private Stream<String> currentStream;
        private Iterator<String> currentIterator;
        private boolean closed;

        private ClosingSegmentSpliterator(Stream<LogFileSegment> segments) {
            super(Long.MAX_VALUE, Spliterator.ORDERED);
            this.segments = segments;
            this.segmentIterator = segments.iterator();
        }

        @Override
        public boolean tryAdvance(Consumer<? super String> action) {
            while (!closed) {
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
            return false;
        }

        private void closeCurrentStream() {
            Stream<String> stream = currentStream;
            currentStream = null;
            currentIterator = null;
            if (stream != null) {
                stream.close();
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                closeCurrentStream();
            } finally {
                segments.close();
            }
        }
    }

    private Stream<String> stream(LogFileMetadata metadata, LinkedList<GCLogFileSegment> segments) throws IOException {
        //todo: find rolling files....
        if (metadata.isPlainText() || metadata.isDirectory()) {
            switch (segments.size()) {
                case 0:
                    String[] empty = new String[0];
                    return Arrays.stream(empty);
                case 1:
                    return segments.getFirst().stream();
                default:
                    // This code removes elements from the list of segments, so work on a copy.
                    LinkedList<GCLogFileSegment> copySegments = new LinkedList<>(segments);
                    Stream<String> allSegments = Stream.concat(copySegments.removeFirst().stream(), copySegments.removeFirst().stream());
                    while (!copySegments.isEmpty())
                        allSegments = Stream.concat(allSegments, copySegments.removeFirst().stream());
                    return allSegments;
            }
        } else if (metadata.isZip()) {
            return streamZipFile();
        } else if (metadata.isGZip()) {
            throw new IOException("Unable to stream GZip files. Please unzip and retry");
        }
        throw new IOException("Unrecognised file type");
    }

    @SuppressWarnings("resource")
    private Stream<String> streamZipFile() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        List<ZipEntry> entries = zipFile.stream().filter(entry -> !entry.isDirectory()).collect(Collectors.toList());
        Vector<InputStream> streams = new Vector<>();

        try {
            entries
                    .stream()
                    .map(entry -> {
                        try {
                            return zipFile.getInputStream(entry);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(streams::add);
        } catch (UncheckedIOException uioe) {
            throw uioe.getCause();
        }

        SequenceInputStream sequenceInputStream = new SequenceInputStream(streams.elements());
        
        return new BufferedReader(new InputStreamReader(sequenceInputStream)).lines();
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

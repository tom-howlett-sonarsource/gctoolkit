// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        if (getMetaData().isDirectory() || getMetaData().isPlainText() || getMetaData().isZip()) {
            Queue<Stream<String>> openedSegmentStreams = new ConcurrentLinkedQueue<>();
            Stream<String> lines = getMetaData().logFiles()
                    .flatMap(segment -> track(segment.stream(), openedSegmentStreams))
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> s.length() > 0);
            return Stream.concat(lines, Stream.of(endOfData()))
                    .onClose(() -> closeAll(openedSegmentStreams));
        } else { // yes, this is returning an empty stream.
            return Stream.of(endOfData());
        }
    }

    private static Stream<String> track(Stream<String> stream, Queue<Stream<String>> openedSegmentStreams) {
        if (stream != null) {
            openedSegmentStreams.add(stream);
            return stream;
        }
        return Stream.empty();
    }

    private static void closeAll(Queue<Stream<String>> streams) {
        Throwable failure = null;
        Stream<String> stream;
        while ((stream = streams.poll()) != null) {
            try {
                stream.close();
            } catch (RuntimeException | Error closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else {
                    failure.addSuppressed(closeFailure);
                }
            }
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure != null) {
            throw (Error) failure;
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

    private Stream<String> streamZipFile() throws IOException {
        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            List<ZipEntry> entries = zipFile.stream().filter(entry -> !entry.isDirectory()).collect(Collectors.toList());
            Vector<InputStream> streams = new Vector<>();

            for (ZipEntry entry : entries) {
                streams.add(zipFile.getInputStream(entry));
            }

            SequenceInputStream sequenceInputStream = new SequenceInputStream(streams.elements());
            BufferedReader reader = new BufferedReader(new InputStreamReader(sequenceInputStream));
            return reader.lines().onClose(() -> close(reader, zipFile));
        } catch (IOException | RuntimeException | Error failure) {
            closeAfterFailure(zipFile, failure);
            throw failure;
        }
    }

    private static void close(BufferedReader reader, ZipFile file) {
        try (ZipFile zipFile = file; BufferedReader bufferedReader = reader) {
            // Closing both resources also releases every stream owned by the archive.
        } catch (IOException ioe) {
            throw new java.io.UncheckedIOException(ioe);
        }
    }

    private static void closeAfterFailure(ZipFile file, Throwable failure) {
        try {
            file.close();
        } catch (IOException ioe) {
            failure.addSuppressed(ioe);
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

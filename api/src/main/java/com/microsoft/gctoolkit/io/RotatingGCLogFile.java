// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
        LogFileMetadata metadata = getMetaData();
        if (!(metadata.isDirectory() || metadata.isPlainText() || metadata.isZip())) {
            return Stream.of(endOfData());
        }

        Stream<String> lines = metadata.isZip()
                ? streamZipFile(metadata)
                : metadata.logFiles().flatMap(LogFileSegment::stream);
        return Stream.concat(
                lines.filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> s.length() > 0),
                Stream.of(endOfData()));
    }

    private Stream<String> streamZipFile(LogFileMetadata metadata) throws IOException {
        List<String> segmentNames;
        try (Stream<LogFileSegment> segments = metadata.logFiles()) {
            segmentNames = segments.map(LogFileSegment::getSegmentName).collect(Collectors.toList());
        }

        ZipFile zipFile = new ZipFile(path.toFile());
        try {
            return segmentNames.stream()
                    .map(zipFile::getEntry)
                    .filter(Objects::nonNull)
                    .flatMap(entry -> streamZipEntry(zipFile, entry))
                    .onClose(() -> close(zipFile));
        } catch (RuntimeException | Error exception) {
            closeOnFailure(zipFile, exception);
            throw exception;
        }
    }

    private static Stream<String> streamZipEntry(ZipFile zipFile, ZipEntry entry) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    zipFile.getInputStream(entry), Charset.defaultCharset()));
            return reader.lines().onClose(() -> close(reader));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void close(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void close(ZipFile zipFile) {
        try {
            zipFile.close();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void closeOnFailure(ZipFile zipFile, Throwable failure) {
        try {
            zipFile.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
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

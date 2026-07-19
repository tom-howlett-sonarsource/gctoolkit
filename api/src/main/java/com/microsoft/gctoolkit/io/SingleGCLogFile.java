// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.support.LogStreamFormat;
import com.microsoft.gctoolkit.io.support.LogStreams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A single GC log file. If the file is a zip or gzip file,
 * then the first entry is the file of interest.
 */
public class SingleGCLogFile extends GCLogFile {

    private SingleLogFileMetadata metadata = null;

    /**
     * Constructor for a single, GC log file.
     * @param path The path to the log file.
     */
    public SingleGCLogFile(Path path) {
        super(path);
    }

    @Override
    public LogFileMetadata getMetaData() throws IOException {
        if (metadata == null) {
            metadata = new SingleLogFileMetadata(path);
        }
        return metadata;
    }

    @Override
    public Stream<String> stream() throws IOException {
        return stream(getMetaData());
    }

    private Stream<String> stream(LogFileMetadata metadata) throws IOException {
        LogStreamFormat format = metadata.getFormat();
        if (format != LogStreamFormat.PLAINTEXT && format != LogStreamFormat.ZIP && format != LogStreamFormat.GZIP) {
            throw new IOException("Unable to read " + path.toString());
        }
        Stream<String> stream = LogStreams.open(metadata.getPath(), format);
        return Stream.concat(stream
                .filter(Objects::nonNull)
                .filter(line -> ! line.isBlank())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                ,Stream.of(endOfData()));
    }
}

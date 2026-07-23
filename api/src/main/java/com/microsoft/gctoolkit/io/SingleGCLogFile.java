// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.logsource.LogSourceStreams;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * A single GC log file. If the file is a zip or gzip file,
 * then the first entry is the file of interest.
 */
public class SingleGCLogFile extends GCLogFile {

    private static final Logger LOGGER = Logger.getLogger(SingleGCLogFile.class.getName());

    /**
     * Constructor for a single, GC log file.
     * @param path The path to the log file.
     */

    private SingleLogFileMetadata metadata = null;

    public SingleGCLogFile(java.nio.file.Path path) {
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
        Stream<String> stream;
        if (metadata.isPlainText()) {
            stream = LogSourceStreams.streamPlain(metadata.getPath());
        } else if (metadata.isZip()) {
            stream = LogSourceStreams.streamZip(metadata.getPath());
        } else if (metadata.isGZip()) {
            stream = LogSourceStreams.streamGZip(metadata.getPath());
        } else {
            throw new IOException("Unable to read " + path.toString());
        }
        return Stream.concat(stream
                .filter(Objects::nonNull)
                .filter(line -> ! line.isBlank())
                .map(String::trim)
                .filter(s -> s.length() > 0)
                ,Stream.of(endOfData()));

    }

}

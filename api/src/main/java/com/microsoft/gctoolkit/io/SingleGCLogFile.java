// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.logsource.LogSourceStreams;

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
        LogFileMetadata logFileMetadata = getMetaData();
        return Stream.concat(
                LogSourceStreams.lines(logFileMetadata.getPath(), logFileMetadata.getFormat())
                        .filter(Objects::nonNull)
                        .filter(line -> !line.isBlank())
                        .map(String::trim)
                        .filter(line -> !line.isEmpty()),
                Stream.of(endOfData()));
    }

}

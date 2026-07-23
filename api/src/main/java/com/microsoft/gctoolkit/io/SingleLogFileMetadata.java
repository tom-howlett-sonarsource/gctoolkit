// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public class SingleLogFileMetadata extends LogFileMetadata {

    private final LogFileSegment logFile;

    public SingleLogFileMetadata(Path path) {
        super(path);
        this.logFile = new GCLogFileSegment(path);
    }

    public Stream<LogFileSegment> logFiles() {
        return List.of(logFile).stream();
    }

    public int getNumberOfFiles() {
        return ( logFile != null) ? 1 : 0;
    }

}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public class SingleLogFileMetadata extends LogFileMetadata {

    private final LogFileSegment logFile;

    public SingleLogFileMetadata(Path path) throws IOException {
        super(path);
        this.logFile = new GCLogFileSegment(path);
    }

    public Stream<LogFileSegment> logFiles() {
        return Stream.of(logFile);
    }

    public int getNumberOfFiles() {
        return 1;
    }

}

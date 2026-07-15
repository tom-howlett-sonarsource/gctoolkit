// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gclogsource.LogSourceIO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public class SingleLogFileMetadata extends LogFileMetadata {

    private final LogFileSegment logFile;
    private final int numberOfFiles;

    public SingleLogFileMetadata(Path path) throws IOException {
        super(path);
        this.logFile = new GCLogFileSegment(path);
        this.numberOfFiles = Math.min(1, LogSourceIO.numberOfFiles(getFormat(), path));
    }

    public Stream<LogFileSegment> logFiles() {
        return List.of(logFile).stream();
    }

    public int getNumberOfFiles() {
        return numberOfFiles;
    }

}

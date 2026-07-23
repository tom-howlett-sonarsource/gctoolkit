// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.logsource.LogSourceFormat;
import com.microsoft.gctoolkit.io.logsource.LogStreamSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private LogSourceFormat sourceFormat = LogSourceFormat.UNKNOWN;
    private final Path path;

    protected LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.sourceFormat = LogStreamSource.detectFormat(path);
    }

    public Path getPath() {
        return path;
    }

    public abstract Stream<LogFileSegment> logFiles();

    /**
     * Return the number of files. Useful if the file is a compressed file which may
     * contain multiple entries.
     * @return The number of files in the file.
     */
    public abstract int getNumberOfFiles();

    /**
     * {@code true} if the file is a Zip compressed file.
     * @return {@code true} if the file is a Zip compressed file.
     */
    public boolean isZip()  {
        return sourceFormat == LogSourceFormat.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file.
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return sourceFormat == LogSourceFormat.GZIP;
    }

    /**
     * {@code true} if the file is a regular file.
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return sourceFormat == LogSourceFormat.PLAIN_TEXT;
    }

    /**
     * {@code true} if the file is a directory.
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return sourceFormat == LogSourceFormat.DIRECTORY;
    }

}

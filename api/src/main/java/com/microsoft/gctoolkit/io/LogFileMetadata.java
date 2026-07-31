// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.logsource.LogSourceDiscovery;
import com.microsoft.gctoolkit.logsource.LogSourceFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private final LogSourceFormat fileFormat;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.fileFormat = LogSourceDiscovery.formatOf(path);
    }

    public Path getPath() {
        return path;
    }

    public abstract Stream<LogFileSegment> logFiles();

    /**
     * The discovered format of the log file. Used to open the log file for reading.
     * @return The format of the log file.
     */
    LogSourceFormat getFormat() {
        return fileFormat;
    }

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
        return fileFormat == LogSourceFormat.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file.
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return fileFormat == LogSourceFormat.GZIP;
    }

    /**
     * {@code true} if the file is a regular file.
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return fileFormat == LogSourceFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the file is a directory.
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return fileFormat == LogSourceFormat.DIRECTORY;
    }

}

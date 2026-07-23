// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.logsource.FileFormat;
import com.microsoft.gctoolkit.logsource.LogSourceStreams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private final FileFormat fileFormat;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.fileFormat = LogSourceStreams.detect(path);
    }

    public Path getPath() {
        return path;
    }

    /**
     * Return the detected file format.
     * @return the {@link FileFormat} of this log source
     */
    public FileFormat getFileFormat() {
        return fileFormat;
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
        return fileFormat == FileFormat.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file.
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return fileFormat == FileFormat.GZIP;
    }

    /**
     * {@code true} if the file is a regular file.
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return fileFormat == FileFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the file is a directory.
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return fileFormat == FileFormat.DIRECTORY;
    }

}

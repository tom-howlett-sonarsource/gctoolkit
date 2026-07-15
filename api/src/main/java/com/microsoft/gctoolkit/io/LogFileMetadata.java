// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gclogsource.LogSourceFormat;
import com.microsoft.gctoolkit.gclogsource.LogSourceIO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private LogSourceFormat fileFormat = LogSourceFormat.UNKNOWN;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        detectFormat();
    }

    public Path getPath() {
        return path;
    }

    protected LogSourceFormat getFormat() {
        return fileFormat;
    }

    public abstract Stream<LogFileSegment> logFiles();

    private void detectFormat() throws IOException {
        fileFormat = LogSourceIO.detectFormat(getPath());
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

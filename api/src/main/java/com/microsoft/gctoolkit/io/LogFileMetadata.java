// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.source.LogFileFormat;
import com.microsoft.gctoolkit.source.LogFileSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private final LogFileSource source;

    public LogFileMetadata(Path path) throws IOException {
        source = LogFileSource.from(path);
    }

    public Path getPath() {
        return source.getPath();
    }

    public abstract Stream<LogFileSegment> logFiles();

    public long getByteSize() throws IOException {
        return source.getByteSize();
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
        return source.getFormat() == LogFileFormat.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file. 
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return source.getFormat() == LogFileFormat.GZIP;
    }

    /**
     * {@code true} if the file is a regular file. 
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return source.getFormat() == LogFileFormat.PLAIN_TEXT;
    }

    /**
     * {@code true} if the file is a directory. 
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return source.getFormat() == LogFileFormat.DIRECTORY;
    }

    LogFileSource source() {
        return source;
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.util.LogFileFormat;
import com.microsoft.gctoolkit.io.util.LogFileFormatDetector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private static final Logger LOG = Logger.getLogger(LogFileMetadata.class.getName());

    private LogFileFormat fileFormat = LogFileFormat.UNKNOWN;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.fileFormat = LogFileFormatDetector.detect(path);
    }

    public Path getPath() {
        return path;
    }

    boolean magic(int field1, int field2) {
        return LogFileFormatDetector.matchesMagic(path, field1, field2);
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
        return fileFormat == LogFileFormat.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file.
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return fileFormat == LogFileFormat.GZIP;
    }

    /**
     * {@code true} if the file is a regular file.
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return fileFormat == LogFileFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the file is a directory.
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return fileFormat == LogFileFormat.DIRECTORY;
    }

}

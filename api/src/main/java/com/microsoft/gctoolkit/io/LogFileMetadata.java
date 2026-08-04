// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.logsource.GCLogSources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private static final Logger LOG = Logger.getLogger(LogFileMetadata.class.getName());

    private final GCLogSources.Format fileFormat;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        GCLogSources.Format discoveredFormat;
        try {
            discoveredFormat = GCLogSources.discover(path);
        } catch (IOException exception) {
            LOG.warning(exception.getMessage());
            discoveredFormat = GCLogSources.Format.PLAIN_TEXT;
        }
        this.fileFormat = discoveredFormat;
    }

    public Path getPath() {
        return path;
    }

    public abstract Stream<LogFileSegment> logFiles();

    GCLogSources.Format getFileFormat() {
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
        return fileFormat == GCLogSources.Format.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file. 
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return fileFormat == GCLogSources.Format.GZIP;
    }

    /**
     * {@code true} if the file is a regular file. 
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return fileFormat == GCLogSources.Format.PLAIN_TEXT;
    }

    /**
     * {@code true} if the file is a directory. 
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return fileFormat == GCLogSources.Format.DIRECTORY;
    }

}

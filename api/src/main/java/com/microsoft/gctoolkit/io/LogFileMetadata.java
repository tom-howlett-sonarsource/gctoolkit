// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.source.GCLogSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private static final Logger LOG = Logger.getLogger(LogFileMetadata.class.getName());

    private final GCLogSource source;
    private final GCLogSource.Format fileFormat;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.source = GCLogSource.from(path);
        GCLogSource.Format discoveredFormat;
        try {
            discoveredFormat = source.format();
        } catch (IOException exception) {
            LOG.warning(exception.getMessage());
            discoveredFormat = GCLogSource.Format.PLAIN_TEXT;
        }
        this.fileFormat = discoveredFormat;
    }

    public Path getPath() {
        return path;
    }

    GCLogSource source() {
        return source;
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
        return hasFormat(GCLogSource.Format.ZIP);
    }

    /**
     * {@code true} if the file is a GZip compressed file. 
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return hasFormat(GCLogSource.Format.GZIP);
    }

    /**
     * {@code true} if the file is a regular file. 
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return hasFormat(GCLogSource.Format.PLAIN_TEXT);
    }

    /**
     * {@code true} if the file is a directory. 
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return hasFormat(GCLogSource.Format.DIRECTORY);
    }

    private boolean hasFormat(GCLogSource.Format expectedFormat) {
        return fileFormat == expectedFormat;
    }
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.logio.GCLogSources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private static final Logger LOG = Logger.getLogger(LogFileMetadata.class.getName());

    static final int GZIP_MAGIC1 = GCLogSources.GZIP_MAGIC1;
    static final int GZIP_MAGIC2 = GCLogSources.GZIP_MAGIC2;

    static final int ZIP_MAGIC1 = GCLogSources.ZIP_MAGIC1;
    static final int ZIP_MAGIC2 = GCLogSources.ZIP_MAGIC2;

    private FileFormat fileFormat = FileFormat.UNKNOWN;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        magic();
    }

    public Path getPath() {
        return path;
    }

    boolean magic(int field1, int field2) {
        return GCLogSources.hasMagic(path, field1, field2);
    }

    public abstract Stream<LogFileSegment> logFiles();

    private void magic() {
        if (getPath().toFile().isDirectory())
            fileFormat = FileFormat.DIRECTORY;
        else if ( magic(GZIP_MAGIC1, GZIP_MAGIC2))
            fileFormat = FileFormat.GZIP;
        else if ( magic(ZIP_MAGIC1, ZIP_MAGIC2))
            fileFormat = FileFormat.ZIP;
        else
            fileFormat = FileFormat.PLAINTEXT;
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

    enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

}

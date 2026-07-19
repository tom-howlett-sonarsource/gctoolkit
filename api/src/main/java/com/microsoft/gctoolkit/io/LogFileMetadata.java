// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.source.GCLogFormat;
import com.microsoft.gctoolkit.io.source.GCLogSourceIO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    static final int GZIP_MAGIC1 = GCLogSourceIO.GZIP_MAGIC1;
    static final int GZIP_MAGIC2 = GCLogSourceIO.GZIP_MAGIC2;

    static final int ZIP_MAGIC1 = GCLogSourceIO.ZIP_MAGIC1;
    static final int ZIP_MAGIC2 = GCLogSourceIO.ZIP_MAGIC2;

    private GCLogFormat fileFormat = GCLogFormat.UNKNOWN;
    private final Path path;

    protected LogFileMetadata(Path path) throws IOException {
        this.path = path;
        fileFormat = GCLogSourceIO.detectFormat(path);
    }

    public Path getPath() {
        return path;
    }

    boolean magic(int field1, int field2) {
        return GCLogSourceIO.hasMagic(path, field1, field2);
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
        return fileFormat == GCLogFormat.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file.
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return fileFormat == GCLogFormat.GZIP;
    }

    /**
     * {@code true} if the file is a regular file.
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return fileFormat == GCLogFormat.PLAINTEXT;
    }

    /**
     * {@code true} if the file is a directory.
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return fileFormat == GCLogFormat.DIRECTORY;
    }
}

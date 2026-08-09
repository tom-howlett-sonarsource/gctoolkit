// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.source.LogFileFormat;
import com.microsoft.gctoolkit.io.source.LogSources;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    static final int GZIP_MAGIC1 = LogSources.GZIP_MAGIC1;
    static final int GZIP_MAGIC2 = LogSources.GZIP_MAGIC2;

    static final int ZIP_MAGIC1 = LogSources.ZIP_MAGIC1;
    static final int ZIP_MAGIC2 = LogSources.ZIP_MAGIC2;

    private final FileFormat fileFormat;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.fileFormat = FileFormat.from(LogSources.detectFormat(path));
    }

    public Path getPath() {
        return path;
    }

    boolean magic(int field1, int field2) {
        return LogSources.matchesMagic(path, field1, field2);
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

    enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN;

        static FileFormat from(LogFileFormat shared) {
            switch (shared) {
                case ZIP: return ZIP;
                case GZIP: return GZIP;
                case PLAINTEXT: return PLAINTEXT;
                case DIRECTORY: return DIRECTORY;
                default: return UNKNOWN;
            }
        }
    }

}

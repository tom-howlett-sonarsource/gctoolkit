// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.io.source.LogSource;
import com.microsoft.gctoolkit.io.source.LogSourceFormat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private FileFormat fileFormat = FileFormat.UNKNOWN;
    private final Path path;

    @SuppressWarnings("java:S1130")
    protected LogFileMetadata(Path path) throws IOException {
        this.path = path;
        detectFormat();
    }

    public Path getPath() {
        return path;
    }

    public abstract Stream<LogFileSegment> logFiles();

    private void detectFormat() {
        LogSourceFormat detected = LogSource.detectFormat(path);
        fileFormat = mapFormat(detected);
    }

    private static FileFormat mapFormat(LogSourceFormat format) {
        switch (format) {
            case DIRECTORY:
                return FileFormat.DIRECTORY;
            case GZIP:
                return FileFormat.GZIP;
            case ZIP:
                return FileFormat.ZIP;
            case PLAINTEXT:
                return FileFormat.PLAINTEXT;
            default:
                return FileFormat.UNKNOWN;
        }
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

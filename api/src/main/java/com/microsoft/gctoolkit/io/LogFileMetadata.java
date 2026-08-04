// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.shared.io.GCLogSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private final FileFormat fileFormat;
    private final GCLogSource source;

    public LogFileMetadata(Path path) throws IOException {
        source = GCLogSource.from(path);
        fileFormat = toFileFormat(source.getFormat());
    }

    public Path getPath() {
        return source.getPath();
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

    private static FileFormat toFileFormat(GCLogSource.Format sourceFormat) {
        switch (sourceFormat) {
            case ZIP:
                return FileFormat.ZIP;
            case GZIP:
                return FileFormat.GZIP;
            case PLAIN_TEXT:
                return FileFormat.PLAINTEXT;
            case DIRECTORY:
                return FileFormat.DIRECTORY;
            default:
                return FileFormat.UNKNOWN;
        }
    }

    enum FileFormat {
        ZIP,
        GZIP,
        PLAINTEXT,
        DIRECTORY,
        UNKNOWN
    }

}

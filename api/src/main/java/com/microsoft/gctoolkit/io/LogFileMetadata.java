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

    private FileFormat fileFormat = FileFormat.UNKNOWN;
    private final Path path;
    private final GCLogSource source;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.source = GCLogSource.from(path);
        discoverFormat();
    }

    public Path getPath() {
        return path;
    }

    GCLogSource getSource() {
        return source;
    }

    public abstract Stream<LogFileSegment> logFiles();

    private void discoverFormat() {
        switch (source.getFormat()) {
            case DIRECTORY:
                fileFormat = FileFormat.DIRECTORY;
                break;
            case GZIP:
                fileFormat = FileFormat.GZIP;
                break;
            case ZIP:
                fileFormat = FileFormat.ZIP;
                break;
            case PLAIN_TEXT:
                fileFormat = FileFormat.PLAINTEXT;
                break;
            default:
                fileFormat = FileFormat.UNKNOWN;
        }
    }

    /**
     * Return the source's stored byte size.
     * @return stored bytes, or zero for a directory source
     */
    public long getByteSize() {
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

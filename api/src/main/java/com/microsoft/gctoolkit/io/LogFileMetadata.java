// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.shared.io.LogFileSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private static final Logger LOG = Logger.getLogger(LogFileMetadata.class.getName());

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private FileFormat fileFormat = FileFormat.UNKNOWN;
    private final Path path;
    private final LogFileSource source;

    @SuppressWarnings("java:S5993")
    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        this.source = LogFileSource.discover(path);
        discoverFormat();
    }

    public Path getPath() {
        return path;
    }

    boolean magic(int field1, int field2) {
        try {
            return LogFileSource.hasMagic(path, field1, field2);
        } catch (IOException ioe) {
            LOG.warning(ioe.getMessage());
        }
        return false;
    }

    public abstract Stream<LogFileSegment> logFiles();

    Stream<String> stream() throws IOException {
        return source.stream();
    }

    /**
     * Return the source file size in bytes.
     *
     * @return The source file size.
     * @throws IOException when the source size cannot be read
     */
    public long getSize() throws IOException {
        return source.size();
    }

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
                break;
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

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.microsoft.gctoolkit.shared.io.LogSource;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    static final int GZIP_MAGIC1 = 0x1F;
    static final int GZIP_MAGIC2 = 0x8b;

    static final int ZIP_MAGIC1 = 0x50;
    static final int ZIP_MAGIC2 = 0x4b;

    private FileFormat fileFormat = FileFormat.UNKNOWN;
    private final Path path;

    public LogFileMetadata(Path path) throws IOException {
        this.path = path;
        fileFormat = toFileFormat(LogSource.discover(path));
    }

    public Path getPath() {
        return path;
    }

    boolean magic(int field1, int field2) {
        try {
            return LogSource.hasMagic(path, field1, field2);
        } catch (IOException ignored) {
            return false;
        }
    }

    public abstract Stream<LogFileSegment> logFiles();

    private static FileFormat toFileFormat(LogSource.Format format) {
        switch (format) {
            case DIRECTORY: return FileFormat.DIRECTORY;
            case GZIP: return FileFormat.GZIP;
            case ZIP: return FileFormat.ZIP;
            case PLAIN: return FileFormat.PLAINTEXT;
            default: return FileFormat.UNKNOWN;
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

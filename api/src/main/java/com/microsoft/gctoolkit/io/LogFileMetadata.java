// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io;

import com.microsoft.gctoolkit.gclog.GCLogSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Meta-data about a {@link FileDataSource}.
 */
public abstract class LogFileMetadata {

    private final GCLogSource source;

    public LogFileMetadata(Path path) throws IOException {
        source = GCLogSource.from(path);
    }

    public Path getPath() {
        return source.path();
    }

    public abstract Stream<LogFileSegment> logFiles();

    /**
     * Return the physical size of the source in bytes.
     *
     * @return the physical source size
     * @throws IOException when the size cannot be read
     */
    public long getByteSize() throws IOException {
        return source.size();
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
        return source.format() == GCLogSource.Format.ZIP;
    }

    /**
     * {@code true} if the file is a GZip compressed file. 
     * @return {@code true} if the file is a GZip compressed file.
     */
    public boolean isGZip() {
        return source.format() == GCLogSource.Format.GZIP;
    }

    /**
     * {@code true} if the file is a regular file. 
     * @return {@code true} if the file is a regular file.
     */
    public boolean isPlainText() {
        return source.format() == GCLogSource.Format.PLAIN_TEXT;
    }

    /**
     * {@code true} if the file is a directory. 
     * @return {@code true} if the file is a directory.
     */
    public boolean isDirectory() {
        return source.format() == GCLogSource.Format.DIRECTORY;
    }

    GCLogSource source() {
        return source;
    }
}

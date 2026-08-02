// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a log source as discovered by {@link GCLogSources#formatOf(java.nio.file.Path)}.
 */
public enum LogFileFormat {

    /**
     * A Zip compressed file.
     */
    ZIP,

    /**
     * A GZip compressed file.
     */
    GZIP,

    /**
     * A regular, uncompressed file.
     */
    PLAINTEXT,

    /**
     * A directory, which may contain log file segments.
     */
    DIRECTORY,

    /**
     * The format could not be determined.
     */
    UNKNOWN;

    /**
     * {@code true} if this is the {@link #ZIP} format.
     * @return {@code true} if this is the {@link #ZIP} format.
     */
    public boolean isZip() {
        return this == ZIP;
    }

    /**
     * {@code true} if this is the {@link #GZIP} format.
     * @return {@code true} if this is the {@link #GZIP} format.
     */
    public boolean isGZip() {
        return this == GZIP;
    }

    /**
     * {@code true} if this is the {@link #PLAINTEXT} format.
     * @return {@code true} if this is the {@link #PLAINTEXT} format.
     */
    public boolean isPlainText() {
        return this == PLAINTEXT;
    }

    /**
     * {@code true} if this is the {@link #DIRECTORY} format.
     * @return {@code true} if this is the {@link #DIRECTORY} format.
     */
    public boolean isDirectory() {
        return this == DIRECTORY;
    }
}

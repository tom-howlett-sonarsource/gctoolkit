// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logfile;

/**
 * The on-disk format of a GC log source as determined by {@link GCLogFileSource#discoverFormat}.
 */
public enum LogFileFormat {
    /** A ZIP compressed archive, possibly containing multiple entries. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** A regular, uncompressed text file. */
    PLAINTEXT,
    /** A directory containing one or more log files. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

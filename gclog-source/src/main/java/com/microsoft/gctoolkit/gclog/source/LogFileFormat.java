// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclog.source;

/**
 * The recognized formats of a GC log source, as determined by
 * {@link GCLogSources#detectFormat(java.nio.file.Path)}.
 */
public enum LogFileFormat {
    /** A regular text file. */
    PLAINTEXT,
    /** A ZIP archive containing one or more log entries. */
    ZIP,
    /** A GZIP compressed log file. */
    GZIP,
    /** A directory containing log files. */
    DIRECTORY,
    /** The format could not be determined (e.g. the file is unreadable). */
    UNKNOWN
}

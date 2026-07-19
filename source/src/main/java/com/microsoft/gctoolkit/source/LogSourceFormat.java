// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

/**
 * Recognised on-disk formats for a GC log source.
 */
public enum LogSourceFormat {
    /** A regular text file. */
    PLAINTEXT,
    /** A ZIP archive of one or more log entries. */
    ZIP,
    /** A GZip-compressed log file. */
    GZIP,
    /** A directory containing one or more log files. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

/**
 * Recognised on-disk formats for a GC log source.
 */
public enum LogSourceFormat {
    /** A ZIP archive containing one or more log entries. */
    ZIP,
    /** A GZIP compressed log file. */
    GZIP,
    /** An uncompressed, plain-text log file. */
    PLAINTEXT,
    /** A directory containing one or more log files. */
    DIRECTORY,
    /** Format could not be determined (e.g. the file is unreadable). */
    UNKNOWN
}

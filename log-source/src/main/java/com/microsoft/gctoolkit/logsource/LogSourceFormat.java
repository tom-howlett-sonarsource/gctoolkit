// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * Detected on-disk format of a GC log source.
 */
public enum LogSourceFormat {
    /** A regular text file. */
    PLAINTEXT,
    /** A GZIP-compressed file. */
    GZIP,
    /** A ZIP archive containing one or more log entries. */
    ZIP,
    /** A directory of log files. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

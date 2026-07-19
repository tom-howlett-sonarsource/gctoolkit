// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.log.source;

/**
 * The format of a GC log source discovered from its bytes on disk.
 */
public enum LogFileFormat {
    /** A ZIP compressed file. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** A regular plain-text file. */
    PLAIN_TEXT,
    /** A directory of GC log files. */
    DIRECTORY,
    /** The source could not be classified. */
    UNKNOWN
}

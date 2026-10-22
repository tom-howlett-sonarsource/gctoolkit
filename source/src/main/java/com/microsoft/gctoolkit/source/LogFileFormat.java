// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

/**
 * The on-disk format of a GC log source, as discovered from its leading bytes.
 */
public enum LogFileFormat {
    /** A ZIP compressed archive. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** An uncompressed, plain text file. */
    PLAINTEXT,
    /** A directory containing log file segments. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

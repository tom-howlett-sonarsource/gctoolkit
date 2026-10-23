// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a GC log source as discovered from the file system and its leading bytes.
 */
public enum LogFileFormat {
    /** A ZIP compressed archive. */
    ZIP,
    /** A GZIP compressed stream. */
    GZIP,
    /** A regular, uncompressed text file. */
    PLAINTEXT,
    /** A directory containing log file segments. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

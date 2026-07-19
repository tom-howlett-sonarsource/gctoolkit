// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.common;

/**
 * The recognised on-disk formats that a GC log source may take.
 */
public enum SourceFormat {
    /** A regular text file. */
    PLAINTEXT,
    /** A ZIP archive whose entries are log files. */
    ZIP,
    /** A GZIP compressed log file. */
    GZIP,
    /** A directory holding one or more log files. */
    DIRECTORY,
    /** Format could not be determined (e.g., file does not exist or is unreadable). */
    UNKNOWN
}

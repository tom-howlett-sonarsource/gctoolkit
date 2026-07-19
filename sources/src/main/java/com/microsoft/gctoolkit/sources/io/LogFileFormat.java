// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.sources.io;

/**
 * The supported physical formats of a GC log source.
 */
public enum LogFileFormat {
    /** A ZIP archive. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** An uncompressed text file. */
    PLAINTEXT,
    /** A directory containing GC log files. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

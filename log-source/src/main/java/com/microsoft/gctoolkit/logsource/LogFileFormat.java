// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The on-disk format of a GC log source, as determined by source discovery.
 */
public enum LogFileFormat {
    /** A ZIP compressed file. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** A regular, uncompressed text file. */
    PLAINTEXT,
    /** A directory. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

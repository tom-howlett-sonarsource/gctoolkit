// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

/**
 * The on-disk format of a GC log source, as discovered from its leading bytes.
 */
public enum SourceFormat {
    /** A ZIP compressed archive. */
    ZIP,
    /** A GZIP compressed file. */
    GZIP,
    /** A regular, uncompressed text file. */
    PLAINTEXT,
    /** A directory (used for rotating logs held as discrete files). */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

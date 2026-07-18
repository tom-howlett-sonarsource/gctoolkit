// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

/**
 * Supported GC log source formats.
 */
public enum GCLogFileFormat {
    /** ZIP archive. */
    ZIP,
    /** GZIP-compressed file. */
    GZIP,
    /** Uncompressed text file. */
    PLAIN_TEXT,
    /** Directory containing GC log files. */
    DIRECTORY
}

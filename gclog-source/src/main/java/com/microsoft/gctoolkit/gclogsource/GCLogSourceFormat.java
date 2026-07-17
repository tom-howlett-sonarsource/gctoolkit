// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

/**
 * Supported GC log source formats.
 */
public enum GCLogSourceFormat {
    /** ZIP archive. */
    ZIP,
    /** GZIP-compressed file. */
    GZIP,
    /** Plain-text file. */
    PLAIN_TEXT,
    /** Filesystem directory. */
    DIRECTORY
}

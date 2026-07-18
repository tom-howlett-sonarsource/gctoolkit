// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.gclogsource;

/**
 * Supported GC log source formats.
 */
public enum LogSourceFormat {
    /** A directory containing log segments. */
    DIRECTORY,
    /** A GZIP-compressed log. */
    GZIP,
    /** A ZIP archive containing one or more logs. */
    ZIP,
    /** An uncompressed log file. */
    PLAIN_TEXT
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

/**
 * Detected format of a GC log source on disk.
 */
public enum LogFormat {
    /** A plain text log file. */
    PLAINTEXT,
    /** A ZIP archive containing one or more log entries. */
    ZIP,
    /** A GZIP compressed log file. */
    GZIP,
    /** A directory containing rotating log segments. */
    DIRECTORY,
    /** Format could not be determined. */
    UNKNOWN
}

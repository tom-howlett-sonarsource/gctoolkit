// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.source;

/**
 * Supported GC log source formats.
 */
public enum LogFileFormat {
    /** Plain text source. */
    PLAIN_TEXT,
    /** ZIP-compressed source. */
    ZIP,
    /** GZIP-compressed source. */
    GZIP,
    /** Directory source. */
    DIRECTORY
}

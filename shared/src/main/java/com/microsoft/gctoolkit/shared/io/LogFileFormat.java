// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.shared.io;

/**
 * Supported GC log source formats.
 */
public enum LogFileFormat {
    /** Plain text file. */
    PLAIN_TEXT,
    /** ZIP archive. */
    ZIP,
    /** GZIP-compressed file. */
    GZIP,
    /** File-system directory. */
    DIRECTORY
}

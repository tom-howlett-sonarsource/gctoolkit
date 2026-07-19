// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logio;

/**
 * Format of a GC log source discovered on disk.
 */
public enum LogFileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

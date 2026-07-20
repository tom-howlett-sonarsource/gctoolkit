// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.support;

/**
 * The on-disk shape of a GC log source: a plain-text file, a ZIP archive, a
 * GZIP-compressed file, a directory of segments, or unknown when the source
 * could not be classified.
 */
public enum LogFileFormat {
    PLAINTEXT,
    ZIP,
    GZIP,
    DIRECTORY,
    UNKNOWN
}

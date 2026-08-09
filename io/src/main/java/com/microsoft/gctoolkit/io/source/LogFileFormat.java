// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

/**
 * The recognized file formats for a GC log source.
 */
public enum LogFileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

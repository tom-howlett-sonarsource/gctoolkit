// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

/**
 * Recognised on-disk formats for a GC log source path.
 */
public enum LogSourceFormat {
    PLAINTEXT,
    ZIP,
    GZIP,
    DIRECTORY,
    UNKNOWN
}

// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * Describes the on-disk format of a GC log file.
 */
public enum FileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

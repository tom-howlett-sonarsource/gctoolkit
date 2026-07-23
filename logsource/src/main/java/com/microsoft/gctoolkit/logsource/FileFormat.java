// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * Describes the on-disk format of a GC log source file.
 */
public enum FileFormat {
    PLAINTEXT,
    ZIP,
    GZIP,
    DIRECTORY,
    UNKNOWN
}

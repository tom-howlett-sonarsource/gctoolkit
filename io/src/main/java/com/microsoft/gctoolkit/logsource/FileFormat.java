// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a GC log source file, determined by inspecting the file's
 * magic bytes.
 */
public enum FileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

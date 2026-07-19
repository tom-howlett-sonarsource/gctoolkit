// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The physical format of a GC log source on disk. Determined by
 * {@link GCLogSource#detect(java.nio.file.Path)}.
 */
public enum GCLogFileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

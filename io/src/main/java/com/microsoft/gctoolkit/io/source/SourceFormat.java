// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

/**
 * The physical format of a GC log source discovered by
 * {@link GCLogSource#detectFormat(java.nio.file.Path)}.
 */
public enum SourceFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

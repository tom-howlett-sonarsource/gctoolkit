// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

/**
 * The on-disk storage format of a GC log source, as determined by
 * {@link GCLogSource#detect(java.nio.file.Path)}.
 */
public enum SourceFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

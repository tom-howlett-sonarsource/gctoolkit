// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The recognised on-disk formats for a GC log source. The classification is
 * done by {@link GCLogSources#detectFormat(java.nio.file.Path)} using magic
 * bytes for compressed formats or by checking whether the target is a
 * directory.
 */
public enum GCLogSourceFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}

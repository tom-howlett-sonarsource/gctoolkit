// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.io.source;

/**
 * The on-disk format of a GC log source, as determined by
 * {@link GCLogSources#detect(java.nio.file.Path)}.
 */
public enum GCLogSourceFormat {
    /** A regular, uncompressed text file. */
    PLAINTEXT,
    /** A ZIP archive containing one or more log entries. */
    ZIP,
    /** A GZIP-compressed single log file. */
    GZIP,
    /** A directory that may contain rotating log segments. */
    DIRECTORY,
    /** Format could not be determined (for example, the path is missing or unreadable). */
    UNKNOWN
}

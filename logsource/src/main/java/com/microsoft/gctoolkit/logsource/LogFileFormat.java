// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a GC log source as discovered by {@link GCLogSource#discoverFormat(java.nio.file.Path)}.
 */
public enum LogFileFormat {
    /**
     * A Zip compressed file which may contain more than one log file.
     */
    ZIP,
    /**
     * A GZip compressed file.
     */
    GZIP,
    /**
     * A regular, uncompressed log file.
     */
    PLAINTEXT,
    /**
     * A directory which may contain log file segments.
     */
    DIRECTORY,
    /**
     * The format of the source could not be determined.
     */
    UNKNOWN
}

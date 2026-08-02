// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a GC log source as determined by {@link GCLogSource#discoverFormat(java.nio.file.Path)}.
 */
public enum LogFileFormat {
    /**
     * A ZIP compressed file which may contain more than one log.
     */
    ZIP,
    /**
     * A GZIP compressed file.
     */
    GZIP,
    /**
     * An uncompressed log file.
     */
    PLAINTEXT,
    /**
     * A directory, typically holding the segments of a rotating log.
     */
    DIRECTORY,
    /**
     * The format could not be determined.
     */
    UNKNOWN
}

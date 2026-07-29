// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The on disk format of a GC log source.
 */
public enum LogFileFormat {
    /**
     * A Zip compressed archive which may contain more than one log.
     */
    ZIP,
    /**
     * A GZip compressed log.
     */
    GZIP,
    /**
     * An uncompressed log.
     */
    PLAINTEXT,
    /**
     * A directory which may contain one or more logs.
     */
    DIRECTORY,
    /**
     * The format could not be determined.
     */
    UNKNOWN
}

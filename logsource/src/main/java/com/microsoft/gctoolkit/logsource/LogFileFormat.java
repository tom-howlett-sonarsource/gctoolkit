// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The formats in which a GC log source may be presented.
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
     * An uncompressed log file.
     */
    PLAINTEXT,
    /**
     * A directory which may contain more than one log file.
     */
    DIRECTORY
}

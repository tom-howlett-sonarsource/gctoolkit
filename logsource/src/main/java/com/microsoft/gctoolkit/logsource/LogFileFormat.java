// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The formats that a GC log source may be presented in.
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
     * A directory containing log files.
     */
    DIRECTORY,
    /**
     * The format has not been determined, or it isn't one that can be read.
     */
    UNKNOWN
}

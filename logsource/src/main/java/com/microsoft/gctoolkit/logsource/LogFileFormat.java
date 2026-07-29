// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The formats a GC log source may be stored in.
 */
public enum LogFileFormat {

    /**
     * A ZIP compressed source which may hold more than one log file.
     */
    ZIP,

    /**
     * A GZIP compressed source.
     */
    GZIP,

    /**
     * An uncompressed source.
     */
    PLAINTEXT,

    /**
     * A directory which may hold more than one log file.
     */
    DIRECTORY
}

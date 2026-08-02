// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The formats a log source may be found in.
 */
public enum LogSourceFormat {
    /**
     * A ZIP compressed file, possibly containing more than one log.
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

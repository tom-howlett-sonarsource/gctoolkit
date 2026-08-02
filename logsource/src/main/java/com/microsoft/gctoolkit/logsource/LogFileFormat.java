// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a GC log source as determined by {@link GCLogSources#discoverFormat(java.nio.file.Path)}.
 */
public enum LogFileFormat {

    /** A ZIP archive which may contain one or more log files. */
    ZIP,
    /** A GZIP compressed log file. */
    GZIP,
    /** An uncompressed, regular log file. */
    PLAINTEXT,
    /** A directory, typically containing rotating log file segments. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

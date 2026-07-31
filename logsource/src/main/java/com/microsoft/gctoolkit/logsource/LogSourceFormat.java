// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * The format of a log source as determined by {@link LogSourceDiscovery#formatOf(java.nio.file.Path)}.
 */
public enum LogSourceFormat {

    /** A Zip compressed file. */
    ZIP,
    /** A GZip compressed file. */
    GZIP,
    /** A regular, uncompressed file. */
    PLAINTEXT,
    /** A directory containing log sources. */
    DIRECTORY,
    /** The format could not be determined. */
    UNKNOWN
}

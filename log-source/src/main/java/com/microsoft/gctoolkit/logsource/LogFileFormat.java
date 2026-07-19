// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.
package com.microsoft.gctoolkit.logsource;

/**
 * Recognised on-disk formats for a GC log source. {@link #detect(java.nio.file.Path)}
 * on {@link LogFileFormatDetector} returns one of these values.
 */
public enum LogFileFormat {
    ZIP,
    GZIP,
    PLAINTEXT,
    DIRECTORY,
    UNKNOWN
}
